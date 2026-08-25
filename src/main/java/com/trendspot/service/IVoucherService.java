package com.trendspot.service;

import com.trendspot.dto.Result;
import com.trendspot.entity.Voucher;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @since 2021-12-22
 */
public interface IVoucherService extends IService<Voucher> {

    Result queryVoucherOfShop(Long shopId);

    /**
     * 添加秒杀券
     * @param voucher 优惠券
     */
    void addSeckillVoucher(Voucher voucher);
}
