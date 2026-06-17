package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IBlogService extends IService<Blog> {

    /**
     * 查询博客详情
     * @param id
     * @return
     */
    Result queryBlogById(Long id);

    /**
     * 查询热门博客
     * @param current
     * @return
     */
    Result queryHotBlog(Integer current);

    /**
     * 点赞博客
     * @param id
     * @return
     */
    Result likeBlog(Long id);

    /**
     * blog点赞排行榜——按照时间先后排列
     *
     * @param id
     * @return
     */
    Result queryBlogLikes(Long id);

    /**
     * 保存blog并推送给粉丝
     * @param blog
     * @return
     */
    Result saveBlog(Blog blog);

    /**
     * 查询用户关注的人的blog
     * @param max
     * @param offset
     * @return
     */
    Result queryBlogofFollow(Long max, Integer offset);
}
