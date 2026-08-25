package com.trendspot.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.trendspot.entity.ShopType;
import com.trendspot.mapper.ShopTypeMapper;
import com.trendspot.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.trendspot.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;

/**
 * 服务实现类
 * ShopTypeMapper：用哪个 Mapper 查数据库。
 * ShopType：查出来的数据对应哪个实体类，也就是店铺类型表。
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 查询所有商铺类型
     *
     * @return 商铺类型列表
     */
    @Override
    public List<ShopType> queryList() {
        String key = RedisConstants.CACHE_SHOP_TYPE_KEY + "list";

        // 1.查询redis中商铺类型列表
        String shopTypeJson = stringRedisTemplate.opsForValue().get(key);

        // 2.存在，返回
        if (StrUtil.isNotBlank(shopTypeJson)) {
            return JSONUtil.toList(shopTypeJson, ShopType.class);
        }

        // 3.不存在，查询数据库      SELECT * FROM tb_shop_type ORDER BY sort ASC;
        List<ShopType> typeList = query().orderByAsc("sort").list();

        // 4.数据库中不存在，返回“店铺类型列表为空”
        if (typeList == null || typeList.isEmpty()) {
            return Collections.emptyList();
        }

        // 5.数据库中存在，写入redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(typeList));

        // 6.返回
        return typeList;
    }
}
