package me.shumei.open.oks.baidutiebaauto;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import me.shumei.open.oks.baidutiebaauto.tools.HttpUtil;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.CookieStore;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.params.CookiePolicy;
import org.apache.http.client.params.HttpClientParams;
import org.apache.http.impl.client.AbstractHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.util.EntityUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Connection.Method;
import org.jsoup.Connection.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

public class BaiduLoginMethod extends CommonData {
	
	//登录的错误类型
	final public static String CUSTOM_COOKIES_KEY = "customLoginErrorType";//Cookies的Key
	/**登录成功*/
	final public static String ERROR_LOGIN_SUCCEED = "0";
	/**用户取消验证码*/
	final public static String ERROR_CANCEL_CAPTCHA = "1";
	/**下载验证码图片错误*/
	final public static String ERROR_DOWN_CAPTCHA = "2";
	/**用户输入的验证码错误*/
	final public static String ERROR_INPUT_CAPTCHA = "3";
	/**账号密码不正确*/
	final public static String ERROR_ACCOUNT_INFO = "4";

	/**
	 * 登录百度通行证 2012-11-6 19:54:41
	 * 可以把百度登录的错误类型附加到Cookies的HashMap里，让Singin签到类的start函数判断是否登录成功
	 * @return HashMap<String, String>
	 * @throws Exception 
	 */
	public static HashMap<String, String> loginBaiduWeb(String user, String pwd) throws Exception
	{
		System.out.println("使用WEB方式登录百度通行证");
		String captchaReason = "登录百度通行证需要验证码\n\n*如验证码无法显示，可进入“任务管理”修改此任务的配置，把登录方式切换为“WAP”或“Android”";
		HashMap<String, String> cookies = new HashMap<String, String>();
		cookies.put(CUSTOM_COOKIES_KEY, ERROR_LOGIN_SUCCEED);//默认为“登录成功”
		for(int i=0;i<RETRY_TIMES;i++)
		{
			String baseUrl = "http://www.baidu.com";
			String loginPageUrl = "https://passport.baidu.com/v2/?login";//登录页面URL
			String loginCheckUrl = "";//检测是否需要验证码URL
			String captchaUrl = "";//验证码图片地址
			String tokenUrl = "https://passport.baidu.com/v2/api/?getapi&tpl=pp&apiver=v3&tt=" + new Date().getTime() + "&class=login&callback=bd__cbs__yfi4d8";
			String loginSubmitUrl = "https://passport.baidu.com/v2/api/?login";//提交登录信息URL
			String codeString = "";//验证码字符串
			
			List<NameValuePair> postDatas = new ArrayList<NameValuePair>();
			cookies = new HashMap<String, String>();
			Response res;
			
			//HttpClient的数据
			String clientStrResult = "";
			CookieStore cookieStore = null;
			
			//取得HttpClient对象，设置UA，超时等
			HttpClient httpClient = HttpUtil.getNewHttpClient();
			HttpProtocolParams.setUserAgent(httpClient.getParams(), UA_BAIDU_PC);
			HttpConnectionParams.setSoTimeout(httpClient.getParams(), TIME_OUT);
			HttpConnectionParams.setConnectionTimeout(httpClient.getParams(), TIME_OUT);
			HttpClientParams.setCookiePolicy(httpClient.getParams(), CookiePolicy.NETSCAPE);
			
			//访问百度登录页面
			HttpGet httpGet = new HttpGet(loginPageUrl);
			HttpResponse httpResponse = httpClient.execute(httpGet);
			if (httpResponse.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
				//取得返回的字符串
				clientStrResult = EntityUtils.toString(httpResponse.getEntity(), "UTF-8");
				//cookieStore = ((AbstractHttpClient) httpClient).getCookieStore();
			}
			//cookies.putAll(HttpUtil.CookieStroeToHashMap(cookieStore));
			
			
			//获取临时token
			httpGet = new HttpGet(tokenUrl);
			httpResponse = httpClient.execute(httpGet);
			if (httpResponse.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
				clientStrResult = EntityUtils.toString(httpResponse.getEntity(), "UTF-8");
			}
			//System.out.println(clientStrResult);
			
			String token = "";
			String tempTokenStr = clientStrResult.replace("bd__cbs__yfi4d8(", "").replace(")", "");
			try {
				JSONObject jsonObj = new JSONObject(tempTokenStr);
				token = jsonObj.getJSONObject("data").getString("token");
			} catch (JSONException e) {
				e.printStackTrace();
			}
			
			//构造检查账号是否需要验证码的URL
			StringBuilder tempSb = new StringBuilder("https://passport.baidu.com/v2/api/?logincheck");
			tempSb.append("&token=" + token);
			tempSb.append("&tpl=pp");
			tempSb.append("&apiver=v3");
			tempSb.append("&tt=" + new Date().getTime());
			tempSb.append("&username=" + user);
			tempSb.append("&isphone=false");
			tempSb.append("&callback=bd__cbs__2n6dcw");
			loginCheckUrl = tempSb.toString();
			
			//检查是否需要填写验证码
			//bd__cbs__2n6dcw({"errInfo":{ "no": "0" }, "data": { "codeString" : "" }})
			//bd__cbs__2n6dcw({"errInfo":{ "no": "0" }, "data": { "codeString" : "0013657473640156B72EE0558F6454AD15C5CDF46FABC147D2D652E3520E0EC16051F4B392A10E504D850B80278AD66264F96247FB808772BB0A7DF2B1B3014F5E17BFFA9A4D3B0036976F00EE3A73D6C526780FCABC9BF40EAB6FA9829B8E6379F734E1D830250C0DEC0976F2CE86D5AB611C6008B20949438C4121C6E59F246F05EE90D22399A50BC55A83ACA2AAE056382FF42B85F0999D988B633E3635FF501F023AB65C4E78" }})
			httpGet = new HttpGet(loginCheckUrl);
			httpResponse = httpClient.execute(httpGet);
			if (httpResponse.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
				clientStrResult = EntityUtils.toString(httpResponse.getEntity(), "UTF-8");
			}
			String resCheckStr = clientStrResult.replace("bd__cbs__2n6dcw(", "").replace(")", "");
			JSONObject resCheckJsonObj = new JSONObject(resCheckStr);
			codeString = resCheckJsonObj.getJSONObject("data").getString("codeString");
			if(codeString.length() > 0 && !codeString.equals("null"))
			{
				//需要填写验证码
				captchaUrl = "http://passport.baidu.com/cgi-bin/genimage?" + codeString;
				if(CaptchaUtil.showCaptcha(captchaUrl, UA_BAIDU_PC, cookies, "百度通行证", user, captchaReason))
				{
					if(CaptchaUtil.captcha_input.length() > 0)
					{
						//获取用户输入的验证码成功
						//不作任何操作....
					}
					else
					{
						//用户取消输入验证码
						cookies.put(CUSTOM_COOKIES_KEY, ERROR_CANCEL_CAPTCHA);
						return cookies;
					}
				}
				else
				{
					//拉取验证码错误
					cookies.put(CUSTOM_COOKIES_KEY, ERROR_DOWN_CAPTCHA);
					return cookies;
				}
			}
			
			//构造百度通行证登录信息
			//不管是不是遇到验证码，都是要提交登录数据的，两种情况下提交的数据都是相同的
			//在碰到验证码时要改变的数据只有验证码的值而已
			//所以上面只进行了“要输入验证码”的判断，获取验证码
			postDatas.add(new BasicNameValuePair("username", user));
			postDatas.add(new BasicNameValuePair("password", pwd));
			postDatas.add(new BasicNameValuePair("staticpage", "https://passport.baidu.com/v3Jump.html"));
			postDatas.add(new BasicNameValuePair("charset", "UTF-8"));
			postDatas.add(new BasicNameValuePair("token", token));
			postDatas.add(new BasicNameValuePair("tpl", "pp"));
			postDatas.add(new BasicNameValuePair("apiver", "v3"));
			postDatas.add(new BasicNameValuePair("tt", String.valueOf(new Date().getTime())));
			postDatas.add(new BasicNameValuePair("codestring", codeString));
			postDatas.add(new BasicNameValuePair("isPhone", "false"));
			postDatas.add(new BasicNameValuePair("safeflg", "0"));
			postDatas.add(new BasicNameValuePair("u", ""));
			postDatas.add(new BasicNameValuePair("verifycode", CaptchaUtil.captcha_input));
			postDatas.add(new BasicNameValuePair("memberPass", "on"));
			postDatas.add(new BasicNameValuePair("ppui_logintime", ""));
			postDatas.add(new BasicNameValuePair("callback", "parent.bd__pcbs__izsubz"));
			
			//提交登录信息
			HttpPost httpPost = new HttpPost(loginSubmitUrl);
			httpPost.setEntity(new UrlEncodedFormEntity(postDatas, "UTF-8"));
			httpResponse = httpClient.execute(httpPost);
			if (httpResponse.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
				clientStrResult = EntityUtils.toString(httpResponse.getEntity(), "UTF-8");
				cookieStore = ((AbstractHttpClient) httpClient).getCookieStore();
			}
			cookies.putAll(HttpUtil.CookieStroeToHashMap(cookieStore));
			
			//当返回的数据不包含验证码加密字符串时，登录成功
			if(clientStrResult.contains("codestring") == false)
			{
				cookies.put(CUSTOM_COOKIES_KEY, ERROR_LOGIN_SUCCEED);
				break;//跳出重试
			}
			captchaReason = "输入的验证码错误，请重新输入";
			cookies.put(CUSTOM_COOKIES_KEY, ERROR_INPUT_CAPTCHA);
		}
		return cookies;
	}
	
	
	/**
	 * 模拟WAP登录百度通行证 2013-1-29 19:14:21
	 * @return
	 * @throws IOException
	 */
	public static HashMap<String, String> loginBaiduWap(String user, String pwd) throws IOException
	{
		System.out.println("使用WAP方式登录百度通行证");
		String captchaReason = "登录百度通行证需要验证码\n\n*进入“任务管理”修改此任务的配置，把登录方式切换为“电脑”可降低验证码出现的概率";
		HashMap<String, String> cookies = new HashMap<String, String>();
		cookies.put(CUSTOM_COOKIES_KEY, ERROR_LOGIN_SUCCEED);//默认为“登录成功”
		for(int i=0;i<RETRY_TIMES;i++)
		{
			String loginPageUrl = "http://wappass.baidu.com/passport/";
			HashMap<String, String> postDatas = new HashMap<String, String>();
			cookies = new HashMap<String, String>();
			Response res;
			Document doc;
			
			//访问百度登录页面
			res = Jsoup.connect(loginPageUrl).userAgent(UA_CHROME).timeout(TIME_OUT).ignoreContentType(true).method(Method.GET).execute();
			cookies.putAll(res.cookies());
			
			doc = res.parse();
			String isphone = doc.getElementsByAttributeValue("name", "isphone").val();
			String vcodestr = doc.getElementsByAttributeValue("name", "vcodestr").val();
			String u = doc.getElementsByAttributeValue("name", "u").val();
			String tpl = doc.getElementsByAttributeValue("name", "tpl").val();
			String ssid = doc.getElementsByAttributeValue("name", "ssid").val();
			String form = doc.getElementsByAttributeValue("name", "form").val();
			String uid = doc.getElementsByAttributeValue("name", "uid").val();
			String pu = doc.getElementsByAttributeValue("name", "pu").val();
			String tn = doc.getElementsByAttributeValue("name", "tn").val();
			String bdcm = doc.getElementsByAttributeValue("name", "bdcm").val();
			String type = doc.getElementsByAttributeValue("name", "type").val();
			String bd_page_type = doc.getElementsByAttributeValue("name", "bd_page_type").val();
			
			postDatas.put("username", user);
			postDatas.put("password", pwd);
			postDatas.put("submit", "登录");
			postDatas.put("isphone", "0");
			postDatas.put("vcodestr", vcodestr);
			postDatas.put("u", u);
			postDatas.put("tpl", tpl);
			postDatas.put("ssid", ssid);
			postDatas.put("form", form);
			postDatas.put("uid", uid);
			postDatas.put("pu", pu);
			postDatas.put("tn", tn);
			postDatas.put("bdcm", bdcm);
			postDatas.put("type", type);
			postDatas.put("bd_page_type", bd_page_type);
			
			//提交登录信息
			res = Jsoup.connect(loginPageUrl).data(postDatas).cookies(cookies).userAgent(UA_CHROME).referrer(loginPageUrl).timeout(TIME_OUT).ignoreContentType(true).method(Method.POST).execute();
			cookies.putAll(res.cookies());
	
			if(res.body().contains("验证码"))
			{
				doc = res.parse();
				isphone = doc.getElementsByAttributeValue("name", "isphone").val();
				vcodestr = doc.getElementsByAttributeValue("name", "vcodestr").val();
				u = doc.getElementsByAttributeValue("name", "u").val();
				tpl = doc.getElementsByAttributeValue("name", "tpl").val();
				ssid = doc.getElementsByAttributeValue("name", "ssid").val();
				form = doc.getElementsByAttributeValue("name", "form").val();
				uid = doc.getElementsByAttributeValue("name", "uid").val();
				pu = doc.getElementsByAttributeValue("name", "pu").val();
				tn = doc.getElementsByAttributeValue("name", "tn").val();
				bdcm = doc.getElementsByAttributeValue("name", "bdcm").val();
				type = doc.getElementsByAttributeValue("name", "type").val();
				bd_page_type = doc.getElementsByAttributeValue("name", "bd_page_type").val();
				
				String captchaUrl = doc.select(".row-padbtm-10 img").first().attr("src");
				if(CaptchaUtil.showCaptcha(captchaUrl, UA_CHROME, cookies, "百度通行证", user, captchaReason) == false)
				{
					cookies.put(CUSTOM_COOKIES_KEY, ERROR_DOWN_CAPTCHA);
					continue;
				}
				
				postDatas = new HashMap<String, String>();//清空上次登录提交的数据，防止影响
				postDatas.put("username", user);
				postDatas.put("password", pwd);
				postDatas.put("verifycode", CaptchaUtil.captcha_input);
				postDatas.put("submit", "登录");
				postDatas.put("isphone", isphone);
				postDatas.put("vcodestr", vcodestr);
				postDatas.put("u", u);
				postDatas.put("tpl", tpl);
				postDatas.put("ssid", ssid);
				postDatas.put("form", form);
				postDatas.put("uid", uid);
				postDatas.put("pu", pu);
				postDatas.put("tn", tn);
				postDatas.put("bdcm", bdcm);
				postDatas.put("type", type);
				postDatas.put("bd_page_type", bd_page_type);
				
				//提交登录信息
				res = Jsoup.connect(loginPageUrl).data(postDatas).cookies(cookies).userAgent(UA_CHROME).referrer(loginPageUrl).timeout(TIME_OUT).ignoreContentType(true).method(Method.POST).execute();
				cookies.putAll(res.cookies());
				
				if(res.body().contains("验证码"))
				{
					cookies.put(CUSTOM_COOKIES_KEY, ERROR_INPUT_CAPTCHA);
					continue;
				}
				else if(res.body().contains("<input type=\"hidden"))
				{
					cookies.put(CUSTOM_COOKIES_KEY, ERROR_ACCOUNT_INFO);
					continue;
				}
				cookies.put(CUSTOM_COOKIES_KEY, ERROR_LOGIN_SUCCEED);
				break;//登录时遇到验证码，输入验证码后登录成功
			}
			else if(res.body().contains("注册"))
			{
				//账号或密码错误
				cookies.put(CUSTOM_COOKIES_KEY, ERROR_ACCOUNT_INFO);
				continue;
			}
			else
			{
				cookies.put(CUSTOM_COOKIES_KEY, ERROR_LOGIN_SUCCEED);//成功登录
				break;//登录时没有遇到验证码，登录成功，跳出重试
			}
		}
		return cookies;
	}

	
	/**
	 * 模拟Android网页版登录百度通行证 2013-2-21 13:16:28
	 * @param user
	 * @param pwd
	 * @return
	 * @throws IOException
	 */
	public static HashMap<String, String> loginBaiduAndroid(String user, String pwd) throws IOException
	{
		System.out.println("使用Android方式登录百度通行证");
		String captchaReason = "登录百度通行证需要验证码\n\n*进入“任务管理”修改此任务的配置，把登录方式切换为“电脑”可降低验证码出现的概率";
		HashMap<String, String> cookies = new HashMap<String, String>();
		cookies.put(CUSTOM_COOKIES_KEY, ERROR_LOGIN_SUCCEED);//默认为“登录成功”
		for(int i=0;i<RETRY_TIMES;i++)
		{
			boolean isNeedCaptcha = false;
			String loginPageUrl = "http://wappass.baidu.com/passport";
			String loginSubmitPage = "http://wappass.baidu.com/passport/login";
			String jscrackUrl = "";
			HashMap<String, String> postDatas = new HashMap<String, String>();
			cookies = new HashMap<String, String>();
			Response res;
			Document doc;
			
			//访问百度登录页面
			res = Jsoup.connect(loginPageUrl).userAgent(UA_ANDROID).timeout(TIME_OUT).ignoreContentType(true).method(Method.GET).execute();
			cookies.putAll(res.cookies());
			
			doc = res.parse();
			String isphone = doc.getElementById("isphone").val();
			String en_rsa_pwd = doc.getElementById("en_rsa_pwd").val();
			String en_rsa_time = doc.getElementById("en_rsa_time").val();
			String vcodestr = doc.getElementById("vcodeStr").val();
			String u = doc.getElementsByAttributeValue("name", "u").val();
			String tpl = doc.getElementsByAttributeValue("name", "tpl").val();
			String ssid = doc.getElementsByAttributeValue("name", "ssid").val();
			String form = doc.getElementsByAttributeValue("name", "form").val();
			String uid = doc.getElementsByAttributeValue("name", "uid").val();
			String pu = doc.getElementsByAttributeValue("name", "pu").val();
			String tn = doc.getElementsByAttributeValue("name", "tn").val();
			String bdcm = doc.getElementsByAttributeValue("name", "bdcm").val();
			String type = doc.getElementsByAttributeValue("name", "type").val();
			String bd_page_type = doc.getElementsByAttributeValue("name", "bd_page_type").val();
			
			postDatas.put("username", user);
			postDatas.put("password", pwd);
			postDatas.put("isphone", isphone);
			postDatas.put("en_rsa_pwd", en_rsa_pwd);
			postDatas.put("en_rsa_time", en_rsa_time);
			postDatas.put("vcodestr", vcodestr);
			postDatas.put("u", u);
			postDatas.put("tpl", tpl);
			postDatas.put("ssid", ssid);
			postDatas.put("form", form);
			postDatas.put("uid", uid);
			postDatas.put("pu", pu);
			postDatas.put("tn", tn);
			postDatas.put("bdcm", bdcm);
			postDatas.put("type", type);
			postDatas.put("bd_page_type", bd_page_type);
			
			if (vcodestr.length() == 0)
			{
				//不需要填写验证码，直接提交登录数据
				//访问nodejs服务器获取登录验证码串
				//把数据提交到在线破解JS的服务器，这个是需要提交密码（账号不提交）到作者服务器的
				//信不过的可以自己架设Node.js服务器或想其他办法破解登录验证串
				//具体信息可参考作者博文：http://shumei.me/exp/node-js-crack-the-javascript-login-encryption-of-yinyuetai.html
				String data = "baidutieba|@@|login_en_rsa_crack|@@|{\"pwd\":\"" + pwd + "\", \"rsa_pwd_str\":\"" + en_rsa_pwd + "\"}";
				jscrackUrl = "http://oks.shumei.me/jscrack.php?data=" + it.sauronsoftware.base64.Base64.encode(data);
				Response resJsCrack = Jsoup.connect(jscrackUrl).userAgent(UA_ANDROID).timeout(TIME_OUT).ignoreContentType(true).method(Method.GET).execute();
				JSONObject jsonObj;
				try {
					jsonObj = new JSONObject(resJsCrack.body());
					en_rsa_pwd = jsonObj.getString("en_rsa_pwd");
					en_rsa_time = String.valueOf(jsonObj.getInt("en_rsa_time"));
				} catch (JSONException e) {
					e.printStackTrace();
				}
				postDatas.put("en_rsa_pwd", en_rsa_pwd);
				postDatas.put("en_rsa_time", en_rsa_time);
				res = Jsoup.connect(loginSubmitPage).data(postDatas).cookies(cookies).userAgent(UA_ANDROID).referrer(loginPageUrl).timeout(TIME_OUT).ignoreContentType(true).method(Method.POST).execute();
				cookies.putAll(res.cookies());
				if(res.body().contains("密码有误"))
				{
					cookies.put(CUSTOM_COOKIES_KEY, ERROR_ACCOUNT_INFO);
					continue;
				} else if (res.body().contains("验证码")) {
					//提交登录信息后被要求填写验证码
					isNeedCaptcha = true;
				}
			} else {
				//需要验证码
				isNeedCaptcha = true;
			}
			
			//检查是否需要填写验证码
			if (isNeedCaptcha)
			{
				doc = res.parse();
				isphone = doc.getElementById("isphone").val();
				en_rsa_pwd = doc.getElementById("en_rsa_pwd").val();
				en_rsa_time = doc.getElementById("en_rsa_time").val();
				vcodestr = doc.getElementById("vcodeStr").val();
				u = doc.getElementsByAttributeValue("name", "u").val();
				tpl = doc.getElementsByAttributeValue("name", "tpl").val();
				ssid = doc.getElementsByAttributeValue("name", "ssid").val();
				form = doc.getElementsByAttributeValue("name", "form").val();
				uid = doc.getElementsByAttributeValue("name", "uid").val();
				pu = doc.getElementsByAttributeValue("name", "pu").val();
				tn = doc.getElementsByAttributeValue("name", "tn").val();
				bdcm = doc.getElementsByAttributeValue("name", "bdcm").val();
				type = doc.getElementsByAttributeValue("name", "type").val();
				bd_page_type = doc.getElementsByAttributeValue("name", "bd_page_type").val();
				
				String captchaUrl = doc.select("form img").first().attr("src");
				if (captchaUrl.startsWith("http://wappass.baidu.com/cgi-bin/genimage?") == false)
				{
					captchaUrl = "http://wappass.baidu.com/cgi-bin/genimage?" + captchaUrl;
				}
				if(CaptchaUtil.showCaptcha(captchaUrl, UA_CHROME, cookies, "百度通行证", user, captchaReason) == false)
				{
					cookies.put(CUSTOM_COOKIES_KEY, ERROR_DOWN_CAPTCHA);
					continue;
				}
				
				//访问nodejs服务器获取登录验证码串
				String data = "baidutieba|@@|login_en_rsa_crack|@@|{\"pwd\":\"" + pwd + "\", \"rsa_pwd_str\":\"" + en_rsa_pwd + "\"}";
				jscrackUrl = "http://oks.shumei.me/jscrack.php?data=" + it.sauronsoftware.base64.Base64.encode(data);
				Response resJsCrack = Jsoup.connect(jscrackUrl).userAgent(UA_ANDROID).timeout(TIME_OUT).ignoreContentType(true).method(Method.GET).execute();
				try {
					JSONObject jsonObj = new JSONObject(resJsCrack.body());
					en_rsa_pwd = jsonObj.getString("en_rsa_pwd");
					en_rsa_time = String.valueOf(jsonObj.getInt("en_rsa_time"));
				} catch (JSONException e) {
					e.printStackTrace();
				}
				
				postDatas = new HashMap<String, String>();//清空上次登录提交的数据，防止影响
				postDatas.put("username", user);
				postDatas.put("password", pwd);
				postDatas.put("verifycode", CaptchaUtil.captcha_input);
				postDatas.put("isphone", isphone);
				postDatas.put("en_rsa_pwd", en_rsa_pwd);
				postDatas.put("en_rsa_time", en_rsa_time);
				postDatas.put("vcodestr", vcodestr);
				postDatas.put("u", u);
				postDatas.put("tpl", tpl);
				postDatas.put("ssid", ssid);
				postDatas.put("form", form);
				postDatas.put("uid", uid);
				postDatas.put("pu", pu);
				postDatas.put("tn", tn);
				postDatas.put("bdcm", bdcm);
				postDatas.put("type", type);
				postDatas.put("bd_page_type", bd_page_type);
				
				//提交登录信息
				res = Jsoup.connect(loginSubmitPage).data(postDatas).cookies(cookies).userAgent(UA_ANDROID).referrer(loginSubmitPage).timeout(TIME_OUT).ignoreContentType(true).method(Method.POST).execute();
				cookies.putAll(res.cookies());
				if(res.body().contains("验证码"))
				{
					cookies.put(CUSTOM_COOKIES_KEY, ERROR_INPUT_CAPTCHA);
					continue;
				}
				else if(res.body().contains("密码有误"))
				{
					cookies.put(CUSTOM_COOKIES_KEY, ERROR_ACCOUNT_INFO);
					continue;
				}
				cookies.put(CUSTOM_COOKIES_KEY, ERROR_LOGIN_SUCCEED);
				break;//登录时遇到验证码，输入验证码后登录成功
			}
			else
			{
				cookies.put(CUSTOM_COOKIES_KEY, ERROR_LOGIN_SUCCEED);
				break;//登录时没有遇到验证码，登录成功，跳出重试
			}
		}
		return cookies;
	}

}
