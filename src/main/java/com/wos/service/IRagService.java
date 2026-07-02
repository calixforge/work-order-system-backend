package com.wos.service;

import com.wos.common.Result;

public interface IRagService {
    Result<String> ask(String question);
}
