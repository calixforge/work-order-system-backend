package com.wos.common;

public class RedisConstants {

    //小时
    public static final Long LOGIN_TOKEN_EXPIRE_HOURS = 24L;
    public static final String LOGIN_USER_TOKEN_KEY = "login:user:token:";

    //小时
    public static final Long DEPT_NAME_EXPIRE_HOURS = 24L;
    public static final String DEPT_NAME_KEY_PREFIX = "dept:name:";

    public static final Long USER_ROLE_EXPIRE_MINUTES = 5L;
    public static final String USER_ROLE_KEY = "user:role:";

}
