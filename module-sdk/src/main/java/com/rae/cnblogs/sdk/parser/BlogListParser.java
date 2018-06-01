package com.rae.cnblogs.sdk.parser;

import android.text.TextUtils;

import com.rae.cnblogs.sdk.AppGson;
import com.rae.cnblogs.sdk.bean.BlogBean;
import com.rae.cnblogs.sdk.bean.BlogType;
import com.rae.cnblogs.sdk.db.DbBlog;
import com.rae.cnblogs.sdk.db.DbFactory;
import com.rae.cnblogs.sdk.utils.ApiUtils;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 博客列表解析器
 * Created by ChenRui on 2016/11/30 00:13.
 */
public class BlogListParser implements IHtmlParser<List<BlogBean>> {

    DbBlog mDbBlog;

    BlogListParser() {
        mDbBlog = DbFactory.getInstance().getBlog();
    }

    /**
     * 同步本地数据库数据
     */
    public static void syncLocalData(DbBlog dbBlog, BlogBean m) {
        BlogBean blog = dbBlog.getBlog(m.getBlogId());
        // 没有入库的情况
        if (blog == null) {
            return;
        }
        m.setIsRead(blog.getIsRead()); // 设置已读状态
        m.setThumbUrls(blog.getThumbUrls()); // 设置博客图片
        if (TextUtils.isEmpty(blog.getThumbUrls())) {
            // 解析图片地址
            String content = dbBlog.getBlogContent(m.getBlogType(), m.getBlogId());
            if (!TextUtils.isEmpty(content)) {
                m.setThumbUrls(createThumbUrls(content));
                // 更新博客
                dbBlog.updateBlog(m);
            }
        }
    }

    /**
     * 获取小图
     *
     * @param content 博文
     */
    private static String createThumbUrls(String content) {
        try {
            List<String> result = new ArrayList<>();
            // 排序的MAP
            Map<String, String> sortMap = new TreeMap<>(new Comparator<Object>() {
                @Override
                public int compare(Object o1, Object o2) {
                    return o1.toString().compareTo(o2.toString());
                }
            });

            Elements elements = Jsoup.parse(content).select("img");
            for (Element element : elements) {
                String src = ApiUtils.getUrl(element.attr("src"));
                // 过滤一些没有用的图片
                if (TextUtils.isEmpty(src) || src.contains(".gif")) {
                    continue;
                }

                // 优先级处理
                // 1、封面图优先，只取一个图片
                if (element.hasAttr("app-cover")) {
                    result.clear();
                    result.add(0, src);
                    break;
                }
                // 2、小图优先
                else if (element.hasAttr("app-thumb")) {
                    String key = element.attr("app-thumb");
                    if (TextUtils.isEmpty(key)) {
                        result.add(src);
                    } else {
                        sortMap.put(key, src);
                    }
                } else {
                    result.add(src);
                }
            }

            if (sortMap.size() > 0) {
                result.addAll(0, sortMap.values());
                sortMap.clear();
            }


            return AppGson.toJson(result);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private String getAvatar(String src) {
        if (TextUtils.isEmpty(src)) {
            return null;
        }
        if (!src.startsWith("http")) {
            src = "http:" + src;
        }
        return src;
    }

    @Override
    public List<BlogBean> parse(Document document, String html) {

        // 解析HTML
        List<BlogBean> result = new ArrayList<>();
        Elements elements = document.select(".post_item");
        String id;
        for (Element item : elements) {
            Elements element = item.select(".post_item_body");
            id = ApiUtils.getNumber(item.select(".digg .diggnum").attr("id"));

            // 博客ID为空不添加
            if (TextUtils.isEmpty(id)) {
                continue;
            }

            BlogBean m = new BlogBean();
            m.setBlogId(id);
            m.setTitle(element.select(".titlelnk").text()); // 标题
            m.setUrl(element.select(".titlelnk").attr("href"));  // 原文链接
            m.setAvatar(getAvatar(element.select(".pfs").attr("src"))); // 头像地址
            m.setSummary(element.select(".post_item_summary").text()); // 摘要
            m.setAuthor(element.select(".lightblue").text()); // 作者
            m.setAuthorUrl(element.select(".lightblue").attr("href")); // 作者博客地址
            m.setBlogApp(ApiUtils.getBlogApp(m.getAuthorUrl()));
            m.setComment(ApiUtils.getCount(ApiUtils.getNumber(element.select(".article_comment .gray").text()))); // 评论
            m.setViews(ApiUtils.getCount(ApiUtils.getNumber(element.select(".article_view .gray").text())));  // 阅读
            m.setPostDate(ApiUtils.getDate(element.select(".post_item_foot").text())); // 发布时间
            m.setLikes(ApiUtils.getCount(item.select(".diggnum").text()));  // 点赞或者是推荐
            m.setBlogType(BlogType.BLOG.getTypeName());
            syncLocalData(mDbBlog, m);
            result.add(m);
        }

        return result;
    }
}
