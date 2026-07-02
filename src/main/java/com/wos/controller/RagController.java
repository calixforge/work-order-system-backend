package com.wos.controller;


import com.wos.common.Result;
import com.wos.service.IRagService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;



@RequestMapping("/rag")
@RestController
@RequiredArgsConstructor
public class RagController {

    private final IRagService ragService;


    @PostMapping("/ask")
    public Result<String> ask(String message) {
        return ragService.ask(message);
    }
}
