package com.cjw.pet.service.impl;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cjw.pet.dao.*;
import com.cjw.pet.dto.request.OrderListBody;
import com.cjw.pet.dto.request.OrderQuery;
import com.cjw.pet.dto.response.OrderDetailsVo;
import com.cjw.pet.dto.response.OrderRowVo;
import com.cjw.pet.mapper.OrderDetailMapper;
import com.cjw.pet.mapper.OrderMapper;
import com.cjw.pet.mapper.OrderStatusMapper;
import com.cjw.pet.pojo.*;
import com.cjw.pet.service.OrderService;
import com.cjw.pet.utils.IdWorker;
import com.cjw.pet.utils.ServletUtils;
import com.cjw.pet.utils.UserUtils;
import com.cjw.pet.dao.*;
import com.cjw.pet.exception.ExceptionResult;
import com.cjw.pet.pojo.*;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;

/**
 * @author cjw
 */
@Service
@Slf4j
public class OrderServiceImpl implements OrderService {

    private final OrderMapper orderMapper;
    private final OrderDao orderDao;
    private final OrderStatusMapper orderStatusMapper;
    private final OrderDetailMapper orderDetailMapper;
    private final OrderDetailDao orderDetailDao;
    private final IdWorker idWorker;
    private final ModelMapper modelMapper;
    private final PetDao petDao;
    private final BackgroundUserDao backgroundUserDao;
    private final UserDao userDao;

    @Autowired
    private UserUtils userUtils;

    public OrderServiceImpl(OrderMapper orderMapper, OrderDao orderDao, OrderStatusMapper orderStatusMapper, OrderDetailMapper orderDetailMapper, OrderDetailDao orderDetailDao, IdWorker idWorker, ModelMapper modelMapper, PetDao petDao, BackgroundUserDao backgroundUserDao, UserDao userDao) {
        this.orderMapper = orderMapper;
        this.orderDao = orderDao;
        this.orderStatusMapper = orderStatusMapper;
        this.orderDetailMapper = orderDetailMapper;
        this.orderDetailDao = orderDetailDao;
        this.idWorker = idWorker;
        this.modelMapper = modelMapper;
        this.petDao = petDao;
        this.backgroundUserDao = backgroundUserDao;
        this.userDao = userDao;
    }

    @Override
    public List<String> createOrder(OrderListBody orderListBody) {
        // 获取登录用户
        User user = userUtils.getUser(ServletUtils.getRequest());
        if (Objects.isNull(user)) {
            throw new ExceptionResult("user","false",null,"请先登陆");
        }
        List<String> orderIds = new ArrayList<>();
        orderListBody.getOrderBodies().forEach(orderBody -> {
            // 生成orderId
            long orderId = idWorker.nextId();
            // 初始化数据
            Order order = modelMapper.map(orderBody,Order.class);
            order.setBuyerNick(user.getUsername());
            order.setCreateTime(new Date());
            order.setOrderId(String.valueOf(orderId));
            order.setUserId(user.getId());
            order.setStatus(1);
            // 保存数据
            this.orderMapper.insert(order);

            // 保存订单状态
            OrderStatus orderStatus = new OrderStatus();
            orderStatus.setOrderId(String.valueOf(orderId));
            orderStatus.setCreateTime(order.getCreateTime());
            // 初始状态为未付款
            orderStatus.setStatus(1);

            this.orderStatusMapper.insert(orderStatus);

            // 订单详情中添加orderId
            orderBody.getOrderDetails().forEach(od -> {
                Integer count1 = petDao.lambdaQuery().eq(Pet::getId, od.getSkuId()).eq(Pet::getCreateId, user.getId()).count();
                if (count1>0) {
                    throw new ExceptionResult("cart","false",null,"不能购买自己发布的商品");
                }
                od.setOrderId(String.valueOf(orderId));
                this.orderDetailMapper.insert(od);
            });
            // 保存订单详情,使用批量插入功能

            log.debug("生成订单，订单编号：{}，用户id：{}", orderId, user.getId());

            orderIds.add(String.valueOf(orderId));
        });

        return orderIds;
    }

    @Override
    public OrderDetailsVo queryById(String id) {
        // 获取登录用户
        User user = userUtils.getUser(ServletUtils.getRequest());
        if (Objects.isNull(user)) {
            throw new ExceptionResult("user","false",null,"请先登陆");
        }
        // 查询订单
        Order order = this.orderMapper.selectById(id);
        if (Objects.isNull(order)) {
            throw new ExceptionResult("user","000001",null,"订单已取消");
        }
        OrderDetailsVo orderDetailsVo = modelMapper.map(order,OrderDetailsVo.class);
        if (order.getBackgroundAgentId()==0) {
            BackgroundUser backgroundUser = backgroundUserDao.getById(order.getBackgroundAgentId());
            orderDetailsVo.setSellerName(Objects.isNull(backgroundUser)? "":backgroundUser.getUsername());
        }else {
            User SellerUser = userDao.getById(order.getSellerId());
            orderDetailsVo.setSellerName(Objects.isNull(SellerUser)? "":SellerUser.getUsername());
        }
        // 查询订单详情
        OrderDetail detail = new OrderDetail();
        detail.setOrderId(id);
        List<OrderDetail> details = this.orderDetailDao
                .lambdaQuery()
                .eq(OrderDetail::getOrderId,id)
                .list();
        orderDetailsVo.setOrderDetails(details);

        // 查询订单状态
        OrderStatus status = this.orderStatusMapper.selectById(order.getOrderId());
        orderDetailsVo.setStatus(status.getStatus());
        return orderDetailsVo;
    }

    @Override
    public PageList<OrderRowVo> queryUserOrderList(Integer page, Integer rows, Integer status) {
        // 获取登录用户
        User user = userUtils.getUser(ServletUtils.getRequest());
        if (Objects.isNull(user)) {
            throw new ExceptionResult("user","false",null,"请先登陆");
        }
        LambdaQueryChainWrapper<Order> lambdaQuery = orderDao.lambdaQuery();
        if (status>0) {
            lambdaQuery.eq(Order::getStatus,status);
        }
        lambdaQuery.orderByDesc(Order::getUpdateTime);
        Page<Order> orderPage = lambdaQuery
                .eq(Order::getUserId, userUtils.getUser(ServletUtils.getRequest()).getId())
                .orderByDesc(Order::getCreateTime)
                .page(new Page<>(page, rows));
        List<OrderRowVo> orderRowVos = new LinkedList<>();
        orderPage.getRecords().forEach(order -> {
            // 查询订单状态
            OrderRowVo orderRowVo = modelMapper.map(order,OrderRowVo.class);
            List<OrderDetail> details = this.orderDetailDao
                    .lambdaQuery()
                    .eq(OrderDetail::getOrderId,order.getOrderId())
                    .list();
            if (order.getBackgroundAgentId()==0) {
                BackgroundUser backgroundUser = backgroundUserDao.getById(order.getBackgroundAgentId());
                orderRowVo.setSellerName(Objects.isNull(backgroundUser)? "":backgroundUser.getUsername());
            }else {
                User SellerUser = userDao.getById(order.getSellerId());
                orderRowVo.setSellerName(Objects.isNull(SellerUser)? "":SellerUser.getUsername());
            }
            orderRowVo.setOrderDetails(details);
            orderRowVos.add(orderRowVo);
        });
        return PageList.of(orderRowVos, orderPage);
    }




    @Override
    public PageList<OrderRowVo> queryOrderList(OrderQuery query) {
        // 获取登录用户
        User user = userUtils.getUser(ServletUtils.getRequest());
        BackgroundUser backgroundUser = userUtils.getBackgroundUser(ServletUtils.getRequest());
        if (Objects.isNull(user)) {
            throw new ExceptionResult("user","false",null,"请先登陆");
        }
        LambdaQueryChainWrapper<Order> lambdaQuery = orderDao.lambdaQuery();
        if (StringUtils.hasText(query.getOrderId())) {
            lambdaQuery.like(Order::getOrderId,query.getOrderId());
        }
//        if (query.getStatus()>0) {
//            lambdaQuery.eq(Order::getBackgroundAgentId,query.getStatus());
//        }
        if (query.getType()!=null) {
            if ( lambdaQuery.eq(Order::getSellerId, backgroundUser.getId()).count()>0) {
            }
        }
        lambdaQuery.orderByDesc(Order::getUpdateTime);
        Page<Order> orderPage = lambdaQuery
                .orderByDesc(Order::getCreateTime)
                .page(new Page<>(query.getPageNum(), query.getPageSize()));
        List<OrderRowVo> orderRowVos = new ArrayList<>();
        orderPage.getRecords().forEach(order -> {
            // 查询订单状态
            OrderRowVo orderRowVo = modelMapper.map(order,OrderRowVo.class);
            List<OrderDetail> details = this.orderDetailDao
                    .lambdaQuery()
                    .eq(OrderDetail::getOrderId,order.getOrderId())
                    .list();
            orderRowVo.setOrderDetails(details);
            orderRowVos.add(orderRowVo);
        });
        return PageList.of(orderRowVos, orderPage);
    }


    @Override
    public Boolean updateStatus(String id, Integer status) {
        // 获取登录用户
        User user = userUtils.getUser(ServletUtils.getRequest());
        if (Objects.isNull(user)) {
            throw new ExceptionResult("user","false",null,"请先登陆");
        }
        OrderStatus record = new OrderStatus();
        record.setOrderId(id);
        record.setStatus(status);
        // 根据状态判断要修改的时间
        switch (status) {
            case 2:
                // 付款
                record.setPaymentTime(new Date());
                break;
            case 3:
                // 发货
                record.setConsignTime(new Date());
                break;
            case 4:
                // 确认收获，订单结束
                record.setEndTime(new Date());
                break;
            case 5:
                // 交易失败，订单关闭
                record.setCloseTime(new Date());
                break;
            default:
                return null;
        }
        int count = this.orderStatusMapper.updateById(record);
        Order Order = new Order();
        Order.setOrderId(id);
        Order.setStatus(status);
        Order.setUpdateTime(new Date());
        orderMapper.updateById(Order);
        return count == 1;
    }

    @Override
    public Integer getOrderCount() {
        // 获取登录用户
        User user = userUtils.getUser(ServletUtils.getRequest());
        if (Objects.isNull(user)) {
            throw new ExceptionResult("user","false",null,"请先登陆");
        }
        return orderDao.lambdaQuery().eq(Order::getUserId,user.getId()).count();
    }

    @Override
    public Boolean deletedOrder(String id) {
        // 获取登录用户
        User user = userUtils.getUser(ServletUtils.getRequest());
        if (Objects.isNull(user)) {
            throw new ExceptionResult("user","false",null,"请先登陆");
        }
        return orderDao.removeById(id);
    }
}
