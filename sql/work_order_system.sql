/*
 智能工单系统 建表脚本
 MySQL 5.7 / utf8mb4 / InnoDB
 库:work_order_system
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- 部门表
-- ----------------------------
DROP TABLE IF EXISTS `department`;
CREATE TABLE `department` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` VARCHAR(50) NOT NULL COMMENT '部门名称',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_del` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除:0否 1是',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='部门表';

-- ----------------------------
-- 用户表
-- ----------------------------
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `username` VARCHAR(50) NOT NULL COMMENT '登录名',
  `password` VARCHAR(100) NOT NULL COMMENT '密码(加密存储)',
  `real_name` VARCHAR(50) NOT NULL COMMENT '真实姓名(显示用)',
  `phone` VARCHAR(20) DEFAULT NULL COMMENT '手机号',
  `avatar_url` VARCHAR(255) DEFAULT NULL COMMENT '头像地址',
  `department_id` BIGINT DEFAULT NULL COMMENT '所属部门id',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态:1启用 0停用',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  UNIQUE KEY `uk_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- ----------------------------
-- 角色表
-- ----------------------------
DROP TABLE IF EXISTS `role`;
CREATE TABLE `role` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` VARCHAR(50) NOT NULL COMMENT '角色名(中文显示)',
  `code` VARCHAR(50) NOT NULL COMMENT '角色编码,如 REVIEWER',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_del` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除:0否 1是',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

-- ----------------------------
-- 用户角色关联表(多对多)
-- ----------------------------
DROP TABLE IF EXISTS `user_role`;
CREATE TABLE `user_role` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` BIGINT NOT NULL COMMENT '用户id',
  `role_id` BIGINT NOT NULL COMMENT '角色id',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_role` (`user_id`, `role_id`) COMMENT '防止重复授权',
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联表';

-- ----------------------------
-- 工单表
-- ----------------------------
DROP TABLE IF EXISTS `workorder`;
CREATE TABLE `workorder` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `title` VARCHAR(100) NOT NULL COMMENT '标题',
  `description` VARCHAR(1000) DEFAULT NULL COMMENT '描述',
  `resolution_summary` VARCHAR(1000) DEFAULT NULL COMMENT '处理结果/解决说明(完成工单时填写)',
  `status` VARCHAR(32) NOT NULL COMMENT '状态(枚举name:DRAFT/PENDING_REVIEW/PENDING_ASSIGN/ACCEPTED/COMPLETED/CLOSED/CANCELED)',
  `priority` TINYINT DEFAULT NULL COMMENT '优先级:1高 2中 3低',
  `creator_id` BIGINT NOT NULL COMMENT '提单人id',
  `assignee_id` BIGINT DEFAULT NULL COMMENT '接单人id(派单前为空)',
  `department_id` BIGINT NOT NULL COMMENT '所属部门id(=提单人部门)',
  `complete_time` DATETIME DEFAULT NULL COMMENT '实际完成时间',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_del` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除:0否 1是',
  PRIMARY KEY (`id`),
  KEY `idx_assignee` (`assignee_id`) COMMENT '派给我的工单',
  KEY `idx_creator` (`creator_id`) COMMENT '我发起的工单',
  KEY `idx_dept_status` (`department_id`, `status`) COMMENT '本部门按状态查'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工单表';

-- ----------------------------
-- 工单流转日志表
-- ----------------------------
DROP TABLE IF EXISTS `workorder_log`;
CREATE TABLE `workorder_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `workorder_id` BIGINT NOT NULL COMMENT '工单id',
  `operator_id` BIGINT NOT NULL COMMENT '操作人id',
  `from_status` VARCHAR(32) DEFAULT NULL COMMENT '原状态',
  `to_status` VARCHAR(32) NOT NULL COMMENT '新状态',
  `event` VARCHAR(32) NOT NULL COMMENT '动作/事件',
  `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注(转派/取消原因等)',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  PRIMARY KEY (`id`),
  KEY `idx_workorder` (`workorder_id`) COMMENT '某工单的流转记录'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工单流转日志表';

SET FOREIGN_KEY_CHECKS = 1;
