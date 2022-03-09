package cn.zhh;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.Keys;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.interactions.Actions;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 主类
 * 修改“小视频首页”和“存放目录”之后运行main函数即可
 *
 * @author Zhou Huanghua
 */
@SuppressWarnings("all")
public class DouyinApplication {

    /**
     * 要下载前几个，下载所有设置为null
     */
    private static final Integer DOWNLOAD_SIZE = 50;

    /**
     * 小视频首页，按需修改
     */
    private static final String MAIN_PAGE_URL = "https://www.douyin.com/user/MS4wLjABAAAAKEB7eweWYwkgl7sfUzY15O8GvhrLUvTk8ws9zfq6GzA";

    /**
     * 存放目录，按需修改
     */
    private static final String FILE_SAVE_DIR = "D:/temp/";

    /**
     * 线程池，按需修改并行数量。实际开发请自定义避免OOM
     */
//    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(3);

    /**
     * 谷歌浏览器参数
     */
    private static final ChromeOptions CHROME_OPTIONS = new ChromeOptions();

    /**
     * 用来存储解析错误的页面
     */
    private static final List<String> ERROR_LIST = new ArrayList<>();

    static {
        // 驱动位置
        // TODO 根据chrome版本，也要更新这个文件   https://chromedriver.chromium.org/
        // 避免出现Only local connections are allowed.提示
        System.setProperty("webdriver.chrome.whitelistedIps", "");
        System.setProperty("webdriver.chrome.driver", "src/main/resources/static/chromedriver.exe");
        // 避免被浏览器检测识别
        CHROME_OPTIONS.setExperimentalOption("excludeSwitches", Collections.singletonList("enable-automation"));
    }

    /**
     * main函数
     *
     * @param args 运行参数
     * @throws InterruptedException 睡眠中断异常
     */
    public static void main(String[] args) throws InterruptedException {
        // 获取小视频列表的div元素，批量处理
        Document mainDoc = Jsoup.parse(getMainPageSource());
        Elements divItems = mainDoc.select("li[class=\"ECMy_Zdt\"]");
        System.out.println("divItems size:" + divItems.size());
        // 这里使用CountDownLatch关闭线程池，只是避免执行完一直没退出
        CountDownLatch countDownLatch;
        if (DOWNLOAD_SIZE != null) {
            if (DOWNLOAD_SIZE < divItems.size()) {
                countDownLatch = new CountDownLatch(DOWNLOAD_SIZE);
            } else {
                countDownLatch = new CountDownLatch(divItems.size());
            }
        } else {
            countDownLatch = new CountDownLatch(divItems.size());
        }

        for (int i = 0; i < divItems.size(); i++) {

            if (DOWNLOAD_SIZE != null) {
                if (i == DOWNLOAD_SIZE) break;
            }

            int finalI = i;
            EXECUTOR.execute(() -> {
                try {
                    DouyinApplication.handleItem(divItems.get(finalI));
                } catch (Exception e) {
                    e.printStackTrace();
                }
                countDownLatch.countDown();
                System.out.println("\n------还剩下" + countDownLatch.getCount() + "个要处理--------");
            });
        }
//        divItems.forEach(item ->
//                EXECUTOR.execute(() -> {
//                    try {
//                        Application.handleItem(item);
//                    } catch (Exception e) {
//                        e.printStackTrace();
//                    }
//                    countDownLatch.countDown();
//                })
//        );
        countDownLatch.await();
        EXECUTOR.shutdown();
        System.out.println("\n-------未成功解析的页面-------");
        for (String s : ERROR_LIST) {
            System.out.println(s);
        }
        System.exit(0);
    }

    /**
     * 获取首页内容
     *
     * @return 首页内容
     * @throws InterruptedException 睡眠中断异常
     */
    private static String getMainPageSource() throws InterruptedException {
        ChromeDriver driver = new ChromeDriver(CHROME_OPTIONS);
        try {
            driver.get(MAIN_PAGE_URL);
            long waitTime = Double.valueOf(Math.max(3, Math.random() * 5) * 1000).longValue();
            TimeUnit.MILLISECONDS.sleep(waitTime);
            long timeout = 10_000;
            // 循环下拉，直到全部加载完成或者超时
            do {
                new Actions(driver).sendKeys(Keys.END).perform();
                TimeUnit.MILLISECONDS.sleep(waitTime);
                timeout -= waitTime;
            } while (!driver.getPageSource().contains("暂时没有更多了")
                    && timeout > 0);
            return driver.getPageSource();
        } finally {
            driver.close();
        }
    }

    /**
     * 处理每个小视频
     *
     * @param div 小视频div标签元素
     * @throws Exception 各种异常
     */
    private static void handleItem(Element div) throws Exception {
        String href = div.getElementsByTag("a").first().attr("href");
        HashMap<String, String> videoMap = getVideoUrl("" + href);
        String src = videoMap.get("src");
        String videoTitle = videoMap.get("videoTitle");

        System.out.println("downloadUrl:\n" + src);
        System.out.println("videoTitle:\n" + videoTitle);

        // 有些blob开头的（可能还有其它）暂不处理
        if (src.startsWith("//")) {
            Connection.Response response = Jsoup.connect("https:" + src)
                    // 解决org.jsoup.UnsupportedMimeTypeException: Unhandled content type. Must be text/*, application/xml, or application/xhtml+xml. Mimetype=video/mp4, URL=
                    .ignoreContentType(true)
                    // The default maximum is 1MB.
                    .maxBodySize(100 * 1024 * 1024)
                    .execute();
//            System.out.println(href.substring(1));
            Files.write(Paths.get(FILE_SAVE_DIR, videoTitle + ".mp4"), response.bodyAsBytes());
            System.out.println("下载完毕：" + videoTitle);
        } else {
            ERROR_LIST.add(href);
            System.out.println("无法解析的src：[" + src + "]");
        }
    }

    /**
     * 获取小视频实际链接
     *
     * @param itemUrl 小视频详情页
     * @return 小视频实际链接
     * @throws InterruptedException 睡眠中断异常
     */
    private static HashMap<String, String> getVideoUrl(String itemUrl) throws InterruptedException {
        System.out.println("itemUrl:" + itemUrl);
        itemUrl = "https:" + itemUrl;
        ChromeDriver driver = new ChromeDriver(CHROME_OPTIONS);
        try {
            driver.get(itemUrl);
            long waitTime = Double.valueOf(Math.max(5, Math.random() * 10) * 1000).longValue();
            long timeout = 50_000;

            Element v;
            /**
             * 循环等待，直到链接出来
             * ※这里可以考虑浏览器驱动自带的显式等待()和隐士等待
             */
            do {
                TimeUnit.MILLISECONDS.sleep(waitTime);
                timeout -= waitTime;
            } while (
//                    (
                    Objects.isNull(v = Jsoup.parse(driver.getPageSource()).getElementsByTag("video").first())
//                    || Objects.isNull(v = v.getElementsByTag("video").first()))
                            && timeout > 0);

            // 获取网页标题
//            Elements videoTitleElements = Jsoup.parse(driver.getPageSource()).getElementsByTag("head").get(0).getElementsByTag("title");
//            String videoTitle = videoTitleElements.text();

            Elements videoTitleElements = Jsoup.parse(driver.getPageSource()).getElementsByClass("z8_VexPf");
            String videoTitle = videoTitleElements.text();
            videoTitle = videoTitle.replaceAll("(\r\n|\r|\n|\n\r)", "");
            videoTitle = videoTitle.replaceAll("\\|", "");


//            Jsoup.parse(driver.getPageSource()).
//            Jsoup.parse(driver.getPageSource()).getElementsByTag("head").get(0).getElementsByTag("title").;
//            String videoTitle = videoTitleElements.text();
//
//            response.xpath('//div[@class="t1"]').xpath('string(.)').extract()[0]

            HashMap<String, String> map = new HashMap<>();
            map.put("videoTitle", videoTitle);
            Elements source = v.getElementsByTag("source");
            v = v.getElementsByTag("source").last();
            String videoUrl = v.attr("src");
            map.put("src", videoUrl);

            return map;
        } finally {
            driver.close();
        }
    }
}
