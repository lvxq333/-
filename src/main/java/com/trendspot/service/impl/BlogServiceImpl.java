package com.trendspot.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trendspot.dto.Result;
import com.trendspot.dto.UserDTO;
import com.trendspot.entity.Blog;
import com.trendspot.entity.Follow;
import com.trendspot.entity.ScrollResult;
import com.trendspot.entity.User;
import com.trendspot.mapper.BlogMapper;
import com.trendspot.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.trendspot.service.IFollowService;
import com.trendspot.service.IUserService;
import com.trendspot.utils.RedisConstants;
import com.trendspot.utils.SystemConstants;
import com.trendspot.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private IUserService userService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IFollowService followService;

    /**
     * 查询博客详情
     *
     * @param id
     * @return
     */
    @Override
    public Result queryBlogById(Long id) {
        Blog blog = getById(id);
        if (blog == null) {
            return Result.fail("查询失败");
        }
        // 查询查看blog的用户
        queryBlogUser(blog);
        // 查询blog是否被点赞
        isBlogLiked(blog);
        return Result.ok(blog);
    }

    /**
     * 查询blog是否被点赞
     *
     * @param blog
     */
    private void isBlogLiked(Blog blog) {
        // 1.获取用户
        Long userId = UserHolder.getUser().getId();
        // 2.判断用户是否已经点赞
        String key = RedisConstants.BLOG_LIKED_KEY + blog.getId();
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        blog.setIsLike(score != null);
    }

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog -> {
                    this.queryBlogUser(blog);
                    this.isBlogLiked(blog);
                }
        );
        return Result.ok(records);
    }

    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }

    /**
     * 点赞博客
     *
     * @param id
     * @return
     */
    @Override
    public Result likeBlog(Long id) {
        // 1.获取用户
        Long userId = UserHolder.getUser().getId();
        // 2.判断用户是否已经点赞
        String key = RedisConstants.BLOG_LIKED_KEY + id;
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        if (score == null) {
            // 3.如果未点赞，则点赞
            // 3.1 数据库点赞数+1
            boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
            // 3.2 保存用户点赞数据到redis中set集合
            if (isSuccess) {
                // 添加到Sorted Set集合中
                stringRedisTemplate.opsForZSet().add(
                        key,        // Sorted Set的 key
                        userId.toString(), // Sorted Set的 value
                        System.currentTimeMillis());// Sorted Set的 score——时间戳
            }
        } else {
            // 4.如果点赞，则取消点赞
            // 4.1 数据库点赞数-1
            boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
            // 4.2 删除redis中用户点赞数据
            if (isSuccess) {
                stringRedisTemplate.opsForZSet().remove(key, userId.toString());
            }
        }
        return Result.ok();
    }

    /**
     * blog点赞排行榜——按照时间先后排列
     *
     * @param id
     * @return
     */
    @Override
    public Result queryBlogLikes(Long id) {
        String key = RedisConstants.BLOG_LIKED_KEY + id;
        // 1.查询top5的点赞用户 zrange key 0 4
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if (top5 == null || top5.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        // 2. 解析出其中的用户id
        List<Long> ids = top5.stream().map(Long::valueOf).collect(Collectors.toList());
        // 3.根据用户id查询用户
        String idStr = StrUtil.join(",", ids);
        List<UserDTO> userDTOS = userService.query()
                // in 查询会出现数据乱序的问题，所以用last()方法来排序
                .in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list()
                .stream().map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());

        // 4.返回用户
        return Result.ok(userDTOS);
    }

    /**
     * 保存blog并推送给粉丝
     *
     * @param blog
     * @return
     */
    @Override
    public Result saveBlog(Blog blog) {
        // 1.获取当前用户
        Long userId = UserHolder.getUser().getId();
        blog.setUserId(userId);
        // 2.保存blog
        boolean success = save(blog);
        if (!success) {
            return Result.fail("发布失败");
        }
        // 3.查询blog的作者的所有粉丝
        // 3.1获取粉丝的id
        List<Follow> follows = followService.query()
                .eq("follow_user_id", userId).list();
        if (follows == null || follows.isEmpty()) {
            // 3.2没有粉丝，直接返回
            return Result.ok(blog.getId());
        }
        // 4.推送blog给所有粉丝
        for (Follow follow : follows) {
            // 4.1获取粉丝id
            Long followUserId = follow.getUserId();
            // 4.2推送blog
            String key = RedisConstants.FEED_KEY + followUserId;
            stringRedisTemplate.opsForZSet()
                    .add(key, blog.getId().toString(), System.currentTimeMillis());
        }
        // 5. 返回id
        return Result.ok(blog.getId());
    }

    /**
     * 分页查询用户关注的人的blog
     *
     * @param max
     * @param offset
     * @return
     */
    @Override
    public Result queryBlogofFollow(Long max, Integer offset) {
        // 1.获取当前用户
        Long userId = UserHolder.getUser().getId();
        // 2.查询收件箱  ZREVRANGEBYSCORE key max 0 WITHSCORES LIMIT offset 5
        // key:用户收件箱id、0 最小分数、max 最大分数、offset 偏移量、5 获取数量
        // 默认得到的顺序是时间戳倒序
        String key = RedisConstants.FEED_KEY + userId;
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(key, 0, max, offset, 5);
        // 2.1 收件箱是否为空
        if (typedTuples == null || typedTuples.isEmpty()) {
            return Result.ok();
        }
        // 2.2 不为空，解析数据
        List<Long> ids = new ArrayList<>(typedTuples.size());
        long minTime = 0;       // 最小时间戳，用于判断分页查询的边界和偏移量
        int os = 1;             // 查询下一页时传人的offset偏移量
        for (ZSetOperations.TypedTuple<String> typedTuple : typedTuples) {
            // 2.3 获取blog的id，并添加到list中
            String id = typedTuple.getValue();
            ids.add(Long.valueOf(id));
            // 2.4 获取分数（时间戳）
            long time = typedTuple.getScore().longValue();
            if (time == minTime) {
                os++;
            } else {
                minTime = time;
                os = 1;
            }
        }
        os = minTime == max ? os + offset : os;
        String idStr = StrUtil.join(",", ids);
        // 2.5 根据id获取blog
        List<Blog> blogs = query().in("id", ids)
                .last("ORDER BY FIELD(id," + idStr + ")").list();

        blogs.forEach(blog -> {
            // 2.6 查询blog有关的用户
            queryBlogUser(blog);
            // 2.7 查询blog是否被点赞
            isBlogLiked(blog);
        });
        ScrollResult scrollResult = new ScrollResult();
        scrollResult.setList(blogs);
        scrollResult.setOffset(os);
        scrollResult.setMinTime(minTime);
        // 2.8 封装blog并返回
        return Result.ok(scrollResult);
    }

    // TODO 分页查询可以考虑用RedisIdWorker来生产自增id，确保不会再sortde_set中出现重复的id
}
