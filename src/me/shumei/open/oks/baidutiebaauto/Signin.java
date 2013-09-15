package me.shumei.open.oks.baidutiebaauto;

import java.io.IOException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import me.shumei.open.oks.baidutiebaauto.tools.MD5;

import org.json.JSONObject;
import org.jsoup.Connection.Method;
import org.jsoup.Connection.Response;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.jsoup.Jsoup;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.telephony.TelephonyManager;

/**
 * 使签到类继承CommonData，以方便使用一些公共配置信息
 * 
 * @author wolforce
 * 
 */
public class Signin extends CommonData {
    String resultFlag = "false";
    String resultStr = "未知错误！";

    String signinUrl = "http://zhidao.baidu.com/submit/user?cm=100509";// 签到URL

    Context context;
    String cfg;

    /**
     * <p>
     * <b>程序的签到入口</b>
     * </p>
     * <p>
     * 在签到时，此函数会被《一键签到》调用，调用结束后本函数须返回长度为2的一维String数组。程序根据此数组来判断签到是否成功
     * </p>
     * 
     * @param ctx
     *            主程序执行签到的Service的Context，可以用此Context来发送广播
     * @param isAutoSign
     *            当前程序是否处于定时自动签到状态<br />
     *            true代表处于定时自动签到，false代表手动打开软件签到<br />
     *            一般在定时自动签到状态时，遇到验证码需要自动跳过
     * @param cfg
     *            “配置”栏内输入的数据
     * @param user
     *            用户名
     * @param pwd
     *            解密后的明文密码
     * @return 长度为2的一维String数组<br />
     *         String[0]的取值范围限定为两个："true"和"false"，前者表示签到成功，后者表示签到失败<br />
     *         String[1]表示返回的成功或出错信息
     */
    public String[] start(Context ctx, boolean isAutoSign, String cfg, String user, String pwd) {
        // 把主程序的Context传送给验证码操作类，此语句在显示验证码前必须至少调用一次
        CaptchaUtil.context = ctx;
        // 标识当前的程序是否处于自动签到状态，只有执行此操作才能在定时自动签到时跳过验证码
        CaptchaUtil.isAutoSign = isAutoSign;

        this.context = ctx;
        this.cfg = cfg;

        try {
            // 存放Cookies的HashMap
            HashMap<String, String> cookies = new HashMap<String, String>();
            // Jsoup的Response
            Response res;

            // 获取自定义配置View里设定的百度登录方式的值，默认使用Android登录
            int baidulogintype = 1;
            try {
                JSONObject jsonObj = new JSONObject(cfg);
                baidulogintype = jsonObj.getInt(ManageTask.KEY_LOGIN_TYPE);
            } catch (Exception e) {
                e.printStackTrace();
            }
            // 根据设定的方式登录，获取Cookies
            switch (baidulogintype) {
                case 0:
                    cookies = BaiduLoginMethod.loginBaiduWeb(user, pwd);
                    break;
                case 1:
                    cookies = BaiduLoginMethod.loginBaiduAndroid(user, pwd);
                    break;
                case 2:
                    cookies = BaiduLoginMethod.loginBaiduWap(user, pwd);
                    break;
            }
            // 判断是否登录成功
            String customLoginErrorType = cookies.get(BaiduLoginMethod.CUSTOM_COOKIES_KEY);
            if (customLoginErrorType.equals(BaiduLoginMethod.ERROR_LOGIN_SUCCEED)) {
                // 登录成功，把标记用的Cookies删除掉
                cookies.remove(BaiduLoginMethod.CUSTOM_COOKIES_KEY);
                // 执行贴吧签到函数
                resultStr = signEachTieba(cookies);
            } else {
                // 登录失败，直接跳出签到函数
                if (customLoginErrorType.equals(BaiduLoginMethod.ERROR_ACCOUNT_INFO)) {
                    resultFlag = "false";
                    resultStr = "登录失败，有可能是账号或密码错误";
                } else if (customLoginErrorType.equals(BaiduLoginMethod.ERROR_CANCEL_CAPTCHA)) {
                    resultFlag = "false";
                    resultStr = "用户取消输入验证码";
                } else if (customLoginErrorType.equals(BaiduLoginMethod.ERROR_DOWN_CAPTCHA)) {
                    resultFlag = "false";
                    resultStr = "拉取验证码错误";
                } else if (customLoginErrorType.equals(BaiduLoginMethod.ERROR_INPUT_CAPTCHA)) {
                    resultFlag = "false";
                    resultStr = "输入的验证码错误";
                }
                return new String[] { resultFlag, resultStr };
            }

        } catch (IOException e) {
            this.resultFlag = "false";
            this.resultStr = "连接超时";
            e.printStackTrace();
        } catch (Exception e) {
            this.resultFlag = "false";
            this.resultStr = "未知错误！";
            e.printStackTrace();
        }

        return new String[] { resultFlag, resultStr };
    }

    /**
     * 循环签到各个贴吧
     * 
     * @param cookies
     */
    private String signEachTieba(HashMap<String, String> cookies) {
        String baseUrl = "http://tieba.baidu.com/f?kw=";
        String signUrl = "http://tieba.baidu.com/sign/add";
        String tbsUrl = "http://tieba.baidu.com/dc/common/tbs";
        String tiebaInfoUrl;
        int succeedNum = 0;
        int failedNum = 0;
        int signedNum = 0;// 已经签到的个数

        int signintype = 2;// 签到的类型，0=>+4经验，1=>+6经验，2=>智能切换
        boolean signintoast = true;// 是否开启进度提示
        int intervalBaseTime = 6;// 基础间隔时间
        int intervalRandTime = 9;// 随机浮动时间
        String tiebaListStr = "";

        try {
            JSONObject cfgJsonObj = new JSONObject(cfg);
            signintype = cfgJsonObj.getInt(ManageTask.KEY_SIGNIN_TYPE);
            signintoast = cfgJsonObj.getBoolean(ManageTask.KEY_SIGNIN_TOAST);
            intervalBaseTime = cfgJsonObj.getInt(ManageTask.KEY_INTERVAL_BASE);
            intervalRandTime = cfgJsonObj.getInt(ManageTask.KEY_INTERVAL_RAND);
            tiebaListStr = cfgJsonObj.getString(ManageTask.KEY_TIEBA_LIST);
        } catch (Exception e) {
            e.printStackTrace();
        }

        ArrayList<String> tiebaList = getTiebaList(tiebaListStr, cookies);
        if (tiebaList.size() == 0) {
            resultFlag = "false";
            return "没有检测到任何贴吧，请执行以下操作：\n1.检查网络连接是否正常，是否在使用cmwap连接（失败率较高）\n2.在配置栏内输入需要签到的贴吧，并用中文或英文逗号隔开，如:李毅，wow，仙剑";
        }

        Response res;
        StringBuilder sb = new StringBuilder();
        for (String tiebaName : tiebaList) {
            // 每个贴吧前面都要加个信息，用StringBuilder可增加效率，避免有多个任务时直接用加号会导致的性能下降
            sb.append(tiebaName);
            sb.append("吧:");
            System.out.println("签到" + tiebaName);

            // 如果签到失败就进行重试
            StringBuilder sbTemp = null;
            boolean isSignSucceed = false;// 是否签到成功
            boolean isSkip = false;// 是否跳过本贴吧
            for (int i = 0; i < RETRY_TIMES; i++) {
                isSkip = false;
                isSignSucceed = false;
                sbTemp = new StringBuilder();// 单个贴吧的StringBuilder
                try {
                    // 访问贴吧信息URL
                    // {"no":0,"error":"","data":{"user_info":{"user_id":774751123,"is_sign_in":0,"user_sign_rank":0,"sign_time":0,"cont_sign_num":0,"cout_total_sing_num":0,"is_org_disabled":0},"forum_info":{"is_on":true,"is_filter":false,"forum_info":{"forum_id":3995,"level_1_dir_name":"\u5355\u673a\u6e38\u620f"},"current_rank_info":{"sign_count":2298,"sign_rank":19,"member_count":83137,"dir_rate":"0.1"},"yesterday_rank_info":{"sign_count":2617,"sign_rank":18,"member_count":82861,"dir_rate":"0.1"},"weekly_rank_info":{"sign_count":2585,"sign_rank":19,"member_count":78709},"monthly_rank_info":{"sign_count":2472,"sign_rank":17,"member_count":73105},"level_1_dir_name":"\u6e38\u620f","level_2_dir_name":"\u5355\u673a\u6e38\u620f"}}}
                    tiebaInfoUrl = "http://tieba.baidu.com/sign/info?kw=" + URLEncoder.encode(tiebaName, "GBK");
                    res = Jsoup.connect(tiebaInfoUrl).cookies(cookies).userAgent(UA_ANDROID).referrer(baseUrl).timeout(TIME_OUT).ignoreContentType(true).method(Method.GET).execute();
                    cookies.putAll(res.cookies());
                    if (res.body().contains("\"is_on\":false")) {
                        sbTemp.append("无需签到(跳过)\n");
                        // resultFlag = "true";
                        isSignSucceed = true;
                        isSkip = true;
                    } else if (res.body().contains("\"is_sign_in\":1")) {
                        sbTemp.append("今日已签(跳过)\n");
                        // resultFlag = "true";
                        isSignSucceed = true;
                        isSkip = true;
                    } else {
                        /* 今日还没签过到 */
                        // 检查当前使用的网络类型
                        String networkType = getNetworkType(context);
                        // 根据配置决定使用何种签到方式，0=>+4经验，1=>+6经验，2=>智能切换
                        if (signintype == 1 || (signintype == 2 && networkType.equals("WIFI"))) {
                            /* WiFi签到，此模式签到获得5点经验，每个贴吧需要消耗大概50KB流量 */
                            System.out.println("使用WIFI签到");
                            String encodedTiebaName = URLEncoder.encode(tiebaName, "GBK");
                            String androidTiebaUrl = "http://wapp.baidu.com/f/?kw=" + encodedTiebaName;
                            // 访问Android版贴吧页面
                            cookies.put("USER_JUMP", "2");// 修改Cookies，强制跳转到智能版页面
                            res = Jsoup.connect(androidTiebaUrl).cookies(cookies).userAgent(UA_ANDROID).referrer(baseUrl).timeout(TIME_OUT).ignoreContentType(true).method(Method.GET).execute();
                            cookies.putAll(res.cookies());
                            String fid = getTiebaFid(res.body());
                            String tbs = getTiebaTbs(res.body());

                            // 模拟手机客户端签到，经验最大加8
                            // {"user_info":{"is_sign_in":"1","user_sign_rank":"1378","sign_time":"1378817297","cont_sign_num":"3","cout_total_sing_num":"90","sign_bonus_point":"8"},"error_code":"0","time":1378817297,"ctime":0,"logid":2897212345}
                            // {"error_code":"160003","error_msg":"\u96f6\u70b9\u65f6\u5206\uff0c\u8d76\u5728\u4e00\u5929\u4f0a\u59cb\u7b7e\u5230\u7684\u4eba\u597d\u591a\uff0c\u4eb2\u8981\u4e0d\u7b49\u51e0\u5206\u949f\u518d\u6765\u7b7e\u5427~","info":[],"time":1378915905,"ctime":0,"logid":705378205}
                            // {"error_code":"160008","error_msg":"\u4f60\u7b7e\u5f97\u592a\u5feb\u4e86\uff0c\u5148\u770b\u770b\u8d34\u5b50\u518d\u6765\u7b7e\u5427:)","info":[],"time":1378915972,"ctime":0,"logid":772575132}
                            signUrl = "http://c.tieba.baidu.com/c/c/forum/sign";
                            String BDUSS = cookies.get("BDUSS");
                            HashMap<String, String> postDatas = encryptSignData(BDUSS, fid, tiebaName, tbs);
                            res = Jsoup.connect(signUrl).data(postDatas).header("Content-Type", "application/x-www-form-urlencoded").cookies(cookies).userAgent(UA_BAIDU_ANDROID).referrer(baseUrl).timeout(TIME_OUT).ignoreContentType(true).method(Method.POST).execute();
                            isSignSucceed = analyseWiFiSignResult(sbTemp, res.parse().text());
                            System.out.println(res.body());
                        } else {
                            /* GPRS、3G签到，此模式签到获得4点经验，消耗流量不到1KB */
                            System.out.println("使用GPRS签到");
                            // 访问贴吧tbs密钥
                            // {"tbs":"534fd4f435a20ba31359288090","is_login":1}
                            res = Jsoup.connect(tbsUrl).cookies(cookies).userAgent(UA_ANDROID).referrer(baseUrl).timeout(TIME_OUT).ignoreContentType(true).method(Method.GET).execute();
                            cookies.putAll(res.cookies());
                            JSONObject jsonObj = new JSONObject(res.body());
                            String tbs = jsonObj.getString("tbs");
                            // 提交签到信息
                            // {"no":1101,"error":"\u4eb2\uff0c\u5df2\u7ecf\u6210\u529f\u7b7e\u5230\u4e86\u54e6~","data":""}
                            // {"no":0,"error":"","data":{"uinfo":{"is_sign_in":1,"user_sign_rank":332,"sign_time":1352188057,"cont_sign_num":1,"cout_total_sing_num":1},"finfo":{"forum_info":{"forum_id":59506,"forum_name":"\u4ed9\u52513","level_1_dir_name":"\u5355\u673a\u6e38\u620f"},"current_rank_info":{"sign_count":332},"level_1_dir_name":"\u6e38\u620f","level_2_dir_name":"\u5355\u673a\u6e38\u620f"},"sign_version":1}}
                            signUrl = "http://tieba.baidu.com/sign/add";
                            res = Jsoup.connect(signUrl).data("ie", "utf-8").data("kw", tiebaName).data("tbs", tbs).cookies(cookies).userAgent(UA_ANDROID).referrer(baseUrl).timeout(TIME_OUT).ignoreContentType(true).method(Method.POST).execute();
                            isSignSucceed = analyseGPRSSignResult(sbTemp, res.parse().text());
                            System.out.println(res.body());
                        }
                    }

                } catch (IOException e) {
                    isSignSucceed = false;
                    sbTemp.append("连接失败\n");
                    e.printStackTrace();
                } catch (Exception e) {
                    isSignSucceed = false;
                    sbTemp.append("未知错误！\n");
                    e.printStackTrace();
                }

                // 如果签到成功就跳出重试循环
                if (isSignSucceed) {
                    succeedNum++;
                    break;
                }
            }

            int intervalTime;
            if (isSkip)
                intervalTime = 0;
            else
                intervalTime = (int) (intervalBaseTime + Math.random() * intervalRandTime);
            // 拼接单个贴吧的签到记录
            StringBuilder sigleTiebaSB = new StringBuilder();
            sigleTiebaSB.append("(");
            sigleTiebaSB.append(intervalTime);
            sigleTiebaSB.append("s↓)");
            sigleTiebaSB.append(sbTemp);

            // 把单个贴吧的签到记录追加到最终输出的StringBuilder上
            sb.append(sigleTiebaSB);

            // 向主程序发送Toast广播，显示签到进度
            if (signintoast) {
                signedNum++;
                StringBuilder msgSB = new StringBuilder();
                msgSB.append(signedNum);
                msgSB.append("/");
                msgSB.append(tiebaList.size());
                msgSB.append(" ");
                msgSB.append(tiebaName);
                if (isSignSucceed) {
                    msgSB.append(" 成功");
                } else {
                    msgSB.append("失败");
                }
                sendShowToastBC(context, msgSB.toString(), true);
            }

            // 签到完一个任务后随机停顿一段时间再去签到下一个任务，以防万一
            try {
                Thread.sleep(1000 * intervalTime);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        failedNum = tiebaList.size() - succeedNum;
        String signinStatistic = "成功" + succeedNum + "个，失败" + failedNum + "个\n\n";
        String noticeStr = "\n模拟客户端签到时，在网页上会显示为+6经验，但实际已获得+8经验";
        // 只要有一个贴吧签到错误，那整个任务就算作失败
        if (failedNum > 0) {
            resultFlag = "false";
        } else {
            resultFlag = "true";
        }
        return signinStatistic + sb.toString() + noticeStr;
    }

    /**
     * 用正则获取贴吧fid
     * 
     * @param htmlStr
     * @return
     */
    private String getTiebaFid(String htmlStr) {
        String fid = "";
        Pattern pattern = Pattern.compile("\"fid\" *: *\"(\\d+)\"");
        Matcher matcher = pattern.matcher(htmlStr);
        if (matcher.find()) {
            fid = matcher.group(1);
        }
        return fid;
    }

    /**
     * 用正则获取贴吧tbs
     * 
     * @param htmlStr
     * @return
     */
    private String getTiebaTbs(String htmlStr) {
        String tbs = "";
        Pattern pattern = Pattern.compile("\"tbs\" *: *\"(\\w+)\"");
        Matcher matcher = pattern.matcher(htmlStr);
        if (matcher.find()) {
            tbs = matcher.group(1);
        }
        return tbs;
    }

    /**
     * 获取贴吧名字的List
     * 
     * @param cfgStr
     * @param cookies
     * @return
     */
    private ArrayList<String> getTiebaList(String cfgStr, HashMap<String, String> cookies) {
        String likeTiebaUrl = "http://tieba.baidu.com/f/like/mylike?pn=";// 喜欢的贴吧列表URL
        ArrayList<String> webNameList = new ArrayList<String>();// 网络中获取的贴吧名
        ArrayList<String> cfgNameList = new ArrayList<String>();// 设置的贴吧黑名单

        // 获取用户设置的黑名单
        String tiebaStr = cfgStr.replace("，", ",");// 把所有中文逗号替换成英文逗号
        if (tiebaStr.length() > 0) {
            String[] tiebaArr = tiebaStr.split(",");
            for (String tiebaName : tiebaArr) {
                cfgNameList.add(tiebaName);
            }
        }

        // 从网络获取账户喜欢的贴吧列表
        for (int i = 0; i < RETRY_TIMES; i++) {
            try {
                Response res;
                res = Jsoup.connect(likeTiebaUrl).cookies(cookies).userAgent(UA_BAIDU_PC).timeout(TIME_OUT).ignoreContentType(true).method(Method.GET).execute();

                // 获取第一页的贴吧列表，省流量
                webNameList.addAll(analyseTiebaName(res.parse()));
                // 判断用户喜欢的贴吧列表是否超过一页，如果超过一页，就要翻页
                if (res.body().contains("class=\"pagination\"")) {
                    // 获取页数
                    String maxPageNoStr = res.parse().select("div.pagination a").last().attr("href").replace("/f/like/mylike?&pn=", "");
                    int maxPageNo = 1;
                    try {
                        maxPageNo = Integer.valueOf(maxPageNoStr);
                    } catch (NumberFormatException e) {
                        maxPageNo = 1;
                    }
                    // 分析第1页后的所有贴吧。因为刚才已经访问过第1页了，从第2页开始获取可节省一次页面访问，省流量，省时间
                    analyseTiebaPageList(likeTiebaUrl, webNameList, cookies, maxPageNo);
                }
                break;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // 用网络获取的贴吧列表和本地设置的黑名单列表作差集运算
        webNameList.removeAll(cfgNameList);
        return webNameList;
    }

    /**
     * 遍历多页贴吧，因为第一页在前面已经获取了，所以从第二页开始获取可节省一个网页的流量
     * 
     * @param likeTiebaUrl
     * @param webNameList
     * @param cookies
     * @param maxPageNo
     */
    private void analyseTiebaPageList(String likeTiebaUrl, ArrayList<String> webNameList, HashMap<String, String> cookies, int maxPageNo) {
        Response res;
        for (int i = 2; i <= maxPageNo; i++) {
            // 开启重试模式
            for (int j = 0; j < RETRY_TIMES; j++) {
                try {
                    res = Jsoup.connect(likeTiebaUrl + i).cookies(cookies).userAgent(UA_BAIDU_PC).timeout(TIME_OUT).referrer(likeTiebaUrl).ignoreContentType(true).method(Method.GET).execute();
                    webNameList.addAll(analyseTiebaName(res.parse()));
                    break;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * 分析页面中的贴吧名
     * 
     * @param doc
     * @return
     */
    private ArrayList<String> analyseTiebaName(Document doc) {
        ArrayList<String> tempNameList = new ArrayList<String>();
        Elements anchors = doc.select("div.forum_table tr td>a");
        for (int i = 0; i < anchors.size(); i++) {
            tempNameList.add(anchors.eq(i).text());
        }
        return tempNameList;
    }

    /**
     * 获取当前手机的网络连接方式
     * 
     * @param context
     * @return 返回“WIFI”或“GPRS”字符串
     */
    public static String getNetworkType(Context context) {
        String netType = "NONE";
        try {
            ConnectivityManager connManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo netInfo = connManager.getActiveNetworkInfo();
            if (netInfo != null) {
                if (netInfo.getType() == ConnectivityManager.TYPE_WIFI) {
                    netType = "WIFI";
                } else if (netInfo.getType() == ConnectivityManager.TYPE_MOBILE) {
                    netType = "GPRS";
                } else {
                    netType = "NONE";
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return netType;
    }

    /**
     * 加密模拟客户端签到的数据
     * 
     * @param BDUSS
     * @param fid
     * @param kw
     * @param tbs
     * @return
     */
    private HashMap<String, String> encryptSignData(String BDUSS, String fid, String kw, String tbs) {
        HashMap<String, String> postDatas = new HashMap<String, String>();
        ArrayList<String[]> dataArr = new ArrayList<String[]>();
        dataArr.add(new String[] { "BDUSS", BDUSS });
        dataArr.add(new String[] { "_client_id", "4.4.2wappc_" + System.currentTimeMillis() + "_618" });
        dataArr.add(new String[] { "_client_type", "2" });
        dataArr.add(new String[] { "_client_version", "4.4.2" });
        dataArr.add(new String[] { "_phone_imei", getIMEI(context) });
        dataArr.add(new String[] { "fid", fid });
        dataArr.add(new String[] { "kw", kw });
        dataArr.add(new String[] { "net_type", "3" });
        dataArr.add(new String[] { "tbs", tbs });

        String sign = "";
        String EncryptKey = "tiebaclient!!!";
        String tempStr = "";
        for (String[] paramArr : dataArr) {
            tempStr += paramArr[0] + "=" + paramArr[1];
        }

        try {
            sign = MD5.md5(URLDecoder.decode(tempStr, "GBK") + EncryptKey);
        } catch (Exception e) {
            e.printStackTrace();
        }

        for (String[] paramArr : dataArr) {
            postDatas.put(paramArr[0], paramArr[1]);
        }
        postDatas.put("sign", sign);
        return postDatas;
    }

    /**
     * 获取手机的IMEI
     * 
     * @return String
     */
    public static String getIMEI(Context context) {
        String imei = "456156451534587";// 随机串
        try {
            TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            imei = telephonyManager.getDeviceId();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return imei;
    }

    /**
     * 根据WiFi签到返回的字符串分析签到的结果
     * 
     * @param sb
     * @param str
     * @return true=>签到成功，false=>签到失败
     */
    private boolean analyseWiFiSignResult(StringBuilder sb, String str) {
        // {"user_info":{"is_sign_in":"1","user_sign_rank":"1378","sign_time":"1378817297","cont_sign_num":"3","cout_total_sing_num":"90","sign_bonus_point":"8"},"error_code":"0","time":1378817297,"ctime":0,"logid":2897212345}
        // {"error_code":"160003","error_msg":"\u96f6\u70b9\u65f6\u5206\uff0c\u8d76\u5728\u4e00\u5929\u4f0a\u59cb\u7b7e\u5230\u7684\u4eba\u597d\u591a\uff0c\u4eb2\u8981\u4e0d\u7b49\u51e0\u5206\u949f\u518d\u6765\u7b7e\u5427~","info":[],"time":1378915905,"ctime":0,"logid":705378205}
        // {"error_code":"160008","error_msg":"\u4f60\u7b7e\u5f97\u592a\u5feb\u4e86\uff0c\u5148\u770b\u770b\u8d34\u5b50\u518d\u6765\u7b7e\u5427:)","info":[],"time":1378915972,"ctime":0,"logid":772575132}
        boolean flag = false;
        try {
            JSONObject jsonObj = new JSONObject(str);
            String error_code = jsonObj.getString("error_code");
            if (error_code.equals("0")) {
                flag = true;
                sb.append("签到成功，共签");
                sb.append(jsonObj.getJSONObject("user_info").getString("cont_sign_num"));
                sb.append("次，+");
                sb.append(jsonObj.getJSONObject("user_info").getString("sign_bonus_point"));
                sb.append("\n");
            } else {
                flag = false;
                String error_msg = jsonObj.getString("error_msg");
                sb.append(error_msg);
                sb.append("\n");
            }
        } catch (Exception e) {
            flag = false;
            sb.append("签到失败\n");
            e.printStackTrace();
        }
        return flag;
    }

    /**
     * 根据GPRS签到返回的字符串分析签到的结果
     * 
     * @param sb
     * @param str
     * @return true=>签到成功，false=>签到失败
     */
    private boolean analyseGPRSSignResult(StringBuilder sb, String str) {
        // {"no":1101,"error":"\u4eb2\uff0c\u5df2\u7ecf\u6210\u529f\u7b7e\u5230\u4e86\u54e6~","data":""}
        // {"no":0,"error":"","data":{"uinfo":{"is_sign_in":1,"user_sign_rank":332,"sign_time":1352188057,"cont_sign_num":1,"cout_total_sing_num":1},"finfo":{"forum_info":{"forum_id":59506,"forum_name":"\u4ed9\u52513","level_1_dir_name":"\u5355\u673a\u6e38\u620f"},"current_rank_info":{"sign_count":332},"level_1_dir_name":"\u6e38\u620f","level_2_dir_name":"\u5355\u673a\u6e38\u620f"},"sign_version":1}}
        boolean flag = false;
        try {
            JSONObject jsonObj = new JSONObject(str);
            int no = jsonObj.getInt("no");
            if (no == 0) {
                flag = true;
                sb.append("签到成功，共签");
                sb.append(jsonObj.getJSONObject("data").getJSONObject("uinfo").getInt("cont_sign_num"));
                sb.append("次\n");
            } else if (no == 1101) {
                // 今天已签到
                flag = true;
                sb.append("亲，你之前已经签过了\n");
            } else if (no == 1001) {
                sb.append("未知错误，请重新试一下\n");
                flag = false;
            } else if (no == 1002) {
                sb.append("服务器开小差了，再签一次试试~\n");
                flag = false;
            } else if (no == 1003) {
                sb.append("服务器打盹了，再签一次叫醒它\n");
                flag = false;
            } else if (no == 1006) {
                sb.append("未知错误，请重新试一下\n");
                flag = false;
            } else if (no == 1007) {
                sb.append("服务器打瞌睡了，再签一次敲醒它\n");
                flag = false;
            } else if (no == 1010) {
                sb.append("签到太频繁了点，休息片刻再来吧：）\n");
                flag = false;
            } else if (no == 1011) {
                sb.append("未知错误，请重试\n");
                flag = false;
            } else if (no == 1023) {
                sb.append("未知错误，请重试\n");
                flag = false;
            } else if (no == 1027) {
                sb.append("未知错误，请重试\n");
                flag = false;
            } else if (no == 9000) {
                sb.append("未知错误，请重试\n");
                flag = false;
            } else if (no == 1012) {
                sb.append("贴吧目录出问题啦，请到贴吧签到吧反馈\n");
                flag = false;
            } else if (no == 1100) {
                sb.append("零点时分，赶在一天伊始签到的人好多，亲要不等几分钟再来签吧~\n");
                flag = false;
            } else if (no == 1102) {
                sb.append("你签得太快了，先看看贴子再来签吧：）\n");
                flag = false;
            } else if (no == 9) {
                sb.append("你在本吧被封禁不能进行当前操作\n");
                flag = false;
            } else if (no == 4) {
                sb.append("请您登陆以后再签到哦~\n");
                flag = false;
            } else {
                sb.append("签到失败\n");
                flag = false;
            }
        } catch (Exception e) {
            flag = false;
            sb.append("签到失败\n");
            e.printStackTrace();
        }
        return flag;
    }

}
