/*
 初始化数据:角色 + 超级管理员
 注意:密码用 BCrypt,无法用 SQL 函数生成,需用程序生成后替换占位(见下方说明)。
*/

-- 角色(5 个)
INSERT INTO `role` (`name`, `code`) VALUES
('提单人', 'SUBMITTER'),
('接单人', 'HANDLER'),
('审核',   'REVIEWER'),
('派单人', 'DISPATCHER'),
('管理员', 'ADMIN');

-- 超级管理员(department_id 留空 = 管理员全局)
-- password  admin123 的 BCrypt 值:
INSERT INTO `user` (`username`, `password`, `real_name`) VALUES
('admin', '$2b$10$8kDC0WUmecyc9y0MHtk5cOGZSrGEJrezdS1VtXWanQuL81ZSP6xSK', '超级管理员');

-- 给 admin 分配「管理员」角色
INSERT INTO `user_role` (`user_id`, `role_id`) VALUES
((SELECT id FROM `user` WHERE username = 'admin'),
 (SELECT id FROM `role` WHERE code = 'ADMIN'));
