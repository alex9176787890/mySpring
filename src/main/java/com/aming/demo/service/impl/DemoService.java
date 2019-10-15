package com.aming.demo.service.impl;

import com.aming.annotation.AMService;
import com.aming.demo.service.IDemoService;

/**
 * 核心业务逻辑
 */

@AMService
// @AMService("a") 给IDemoService 的实现类 取一个自定义名字
public class DemoService implements IDemoService {
    public String get(String name){
        return "My name is " + name;
    }

}
