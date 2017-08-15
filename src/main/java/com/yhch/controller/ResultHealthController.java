package com.yhch.controller;


import com.github.pagehelper.PageInfo;
import com.yhch.bean.CommonResult;
import com.yhch.bean.Constant;
import com.yhch.bean.Identity;
import com.yhch.bean.PageResult;
import com.yhch.bean.health.ResultHealthExtend;
import com.yhch.bean.rolecheck.RequiredRoles;
import com.yhch.bean.user.UserExtend;
import com.yhch.pojo.ResultHealth;
import com.yhch.pojo.User;
import com.yhch.service.ResultHealthService;
import com.yhch.service.UserService;
import com.yhch.util.TimeUtil;
import com.yhch.util.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;
import java.util.Date;
import java.util.List;
import java.util.Map;

@RequestMapping("health")
@RestController
public class ResultHealthController {

    private static final Logger logger = LoggerFactory.getLogger(ResultHealthController.class);

    @Autowired
    private ResultHealthService resultHealthService;

    @Autowired
    private UserService userService;


    /**
     * 添加一条健康摘要
     *
     * @param params
     * @param session
     * @return
     */
    @RequestMapping(method = RequestMethod.POST)
    public CommonResult addResultHealthRecord(@RequestBody Map<String, Object> params, HttpSession session) {
        Integer userId = (Integer) params.get("userId");

        {
            User record = new User();
            record.setId(userId);
            if (this.userService.queryOne(record).getRole().equals(Constant.USER_1)) {
                // 1级用户没有此权限
                return CommonResult.failure("一级用户无此权限");
            }
        }

        Integer secondId = (Integer) params.get("secondId");
        Integer inputerId = Integer.valueOf(((Identity) session.getAttribute(Constant.IDENTITY)).getId());
        String contentNew = (String) params.get("contentNew");
        String status = Constant.LU_RU_ZHONG; // 初始状态为录入中
        String note = (String) params.get(Constant.NOTE);
        Date time = TimeUtil.parseTime((String) params.get(Constant.TIME));

        ResultHealth resultHealth = new ResultHealth();
        resultHealth.setUserId(userId);
        resultHealth.setSecondId(secondId);
        resultHealth.setInputerId(inputerId);
        resultHealth.setStatus(status);
        resultHealth.setNote(note);
        resultHealth.setContent(""); // 添加完是空的，审核通过
        resultHealth.setContentNew(contentNew);

        resultHealth.setTime(TimeUtil.getCurrentTime()); // 添加一条健康摘要的时候不输入时间
        resultHealth.setUploadTime(TimeUtil.getCurrentTime());

        this.resultHealthService.save(resultHealth);

        return CommonResult.success("添加成功");
    }


    /**
     * 显示用户列表，实际上和input那里是一致的
     *
     * @param params
     * @param session
     * @return
     */
    @RequestMapping(value = "list", method = RequestMethod.POST)
    @RequiredRoles(roles = {"系统管理员", "二级用户", "顾问部员工", "顾问部主管"})
    public CommonResult queryResultHealthUserList(@RequestBody Map<String, Object> params, HttpSession session) {

        Integer pageNow = (Integer) params.get(Constant.PAGE_NOW);
        Integer pageSize = (Integer) params.get(Constant.PAGE_SIZE);
        String userName = (String) params.get("userName");
        String memberNum = (String) params.get("memberNum");
        Identity identity = (Identity) session.getAttribute(Constant.IDENTITY);

        // 过期的用户看不了
        if (!this.userService.checkValid(identity.getId())) {
            return CommonResult.failure("过期无效的用户");
        }

        List<User> userList = this.resultHealthService.queryResultHealthUserList(identity, userName, memberNum,
                pageNow, pageSize);
        PageResult pageResult = new PageResult(new PageInfo<>(userList));

        List<UserExtend> userExtendList = this.userService.extendFromUser(userList);
        pageResult.setData(userExtendList);

        return CommonResult.success("查询成功", pageResult);
    }


    /**
     * 更新（保存按钮）健康摘要
     *
     * @param healthId
     * @param params
     * @return
     */
    @RequestMapping(value = "{healthId}", method = RequestMethod.PUT)
    public CommonResult updateResultHealthValue(@PathVariable("healthId") Integer healthId, @RequestBody Map<String,
            String> params) {

        String contentNew = params.get("contentNew");

        ResultHealth resultHealth = new ResultHealth();
        resultHealth.setId(healthId);
        resultHealth.setContentNew(contentNew);

        this.resultHealthService.updateSelective(resultHealth);

        return CommonResult.success("更新成功");
    }


    /**
     * 根据userId查询单个member的健康摘要列表
     *
     * @param userId
     * @return
     */
    @RequestMapping(value = "list/{userId}", method = RequestMethod.POST)
    @ResponseBody
    public CommonResult queryHealthListByUserId(@PathVariable("userId") Integer userId, HttpSession session,
                                                @RequestBody Map<String, Object> params) {

        Identity identity = (Identity) session.getAttribute(Constant.IDENTITY);

        String status = (String) params.get(Constant.STATUS);
        Integer secondId = (Integer) params.get(Constant.SECOND_ID);
        Date beginTime = TimeUtil.parseTime((String) params.get("beginTime"));
        Date endTime = TimeUtil.parseTime((String) params.get("endTime"));

        List<ResultHealth> resultHealthList = this.resultHealthService.queryResultHealthListByUserId(identity,
                userId, status, secondId, beginTime, endTime);

        List<ResultHealthExtend> resultHealthExtendList = this.resultHealthService.extendFromResultHealthList
                (resultHealthList);

        return CommonResult.success("查询成功", resultHealthExtendList);
    }


    /**
     * 改变状态
     *
     * @param healthId
     * @param params
     * @param session
     * @return
     */
    @RequestMapping(value = "status/{healthId}", method = RequestMethod.PUT)
    public CommonResult submitOriginRecord(@PathVariable("healthId") Integer healthId, @RequestBody Map<String, Object>
            params, HttpSession session) {

        ResultHealth resultHealth = this.resultHealthService.queryById(healthId);

        // 录入中，待审核，未通知，已通过
        String status = (String) params.get(Constant.STATUS);
        String reason = (String) params.get(Constant.REASON);

        if (Validator.checkEmpty(status)) {
            return CommonResult.failure("修改失败，缺少参数");
        }

        // checker
        Identity identity = (Identity) session.getAttribute(Constant.IDENTITY);
        String identityRole = identity.getRole();
        Integer checkerId = Integer.valueOf(identity.getId());
        String checkerName = this.userService.queryById(checkerId).getName();

        if (status.equals(Constant.DAI_SHEN_HE)) { // 提交，待审核

            // 状态改为'待审核'
            resultHealth.setStatus(Constant.DAI_SHEN_HE);
            this.resultHealthService.update(resultHealth);

            return CommonResult.success("提交成功");

        } else if (status.equals(Constant.WEI_TONG_GUO)) { // 未通过

            // 具有通过和未通过两项权利的人只有主管和ADMIN
            if (!this.userService.checkManager(identityRole) && !this.userService.checkAdmin(identityRole)) {
                return CommonResult.failure("无此权限");
            }

            if (Validator.checkEmpty(reason)) {
                reason = "<未说明原因>";
            }

            resultHealth.setCheckerId(checkerId);

            resultHealth.setStatus(Constant.WEI_TONG_GUO);
            resultHealth.setReason(reason);
            this.resultHealthService.update(resultHealth);

            return CommonResult.success("操作成功");

        } else if (status.equals(Constant.YI_TONG_GUO)) { // 通过，已通过

            // 具有通过和未通过两项权利的人只有主管和ADMIN
            if (!this.userService.checkManager(identityRole) && !this.userService.checkAdmin(identityRole)) {
                return CommonResult.failure("无此权限");
            }

            resultHealth.setCheckerId(checkerId);

            // 通过审核后就覆盖旧的
            resultHealth.setContent(resultHealth.getContentNew());

            resultHealth.setStatus(Constant.YI_TONG_GUO);
            this.resultHealthService.update(resultHealth);

            return CommonResult.success("操作成功");
        } else {
            return CommonResult.failure("参数错误");
        }
    }
}
