package com.campus.market.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.market.entity.Order;
import org.apache.ibatis.annotations.Update;

/**
 * 订单 Mapper（02§2.3.1）。
 * 商品状态三方法签名对应 02§2.3.3（新增 now 参数用于维护 updated_at，实现细节）；
 * 订单状态流转一律带状态条件（乐观锁语义，先到先得，02§2.6.3）。
 */
public interface OrderMapper extends BaseMapper<Order> {

    /**
     * 下单互斥（BR-06）：仅 ON_SALE 可锁定；影响行数=0 表示已被锁定/非在售。
     */
    @Update("UPDATE goods SET status = 'LOCKED', updated_at = #{now} "
            + "WHERE id = #{goodsId} AND status = 'ON_SALE' AND deleted = 0")
    int tryLockGoods(Long goodsId, Long now);

    /**
     * 订单取消/超时关闭/仲裁取消后回滚商品在售（FR-ORDER-03/04，02§2.6.6 同事务）。
     */
    @Update("UPDATE goods SET status = 'ON_SALE', updated_at = #{now} "
            + "WHERE id = #{goodsId} AND status = 'LOCKED'")
    int unlockGoods(Long goodsId, Long now);

    /**
     * 订单完成后标记商品售出（FR-ORDER-05，02§2.6.6 同事务）。
     */
    @Update("UPDATE goods SET status = 'SOLD', updated_at = #{now} "
            + "WHERE id = #{goodsId} AND status = 'LOCKED'")
    int markSold(Long goodsId, Long now);

    /**
     * 模拟支付（BR-04）：条件更新，行数=1 表示从 PENDING_PAY → PAID 成功。
     */
    @Update("UPDATE orders SET status = 'PAID', pay_voucher_no = #{voucher}, pay_time = #{now} "
            + "WHERE id = #{orderId} AND status = 'PENDING_PAY'")
    int markPaid(Long orderId, String voucher, Long now);
}