package com.aming.demo.mvc;

import com.aming.annotation.AMAutowired;
import com.aming.annotation.AMController;
import com.aming.annotation.AMRequestMapping;
import com.aming.annotation.AMRequestParam;
import com.aming.demo.service.IDemoService;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@AMController
@AMRequestMapping("/demo")
public class DemoController {

    @AMAutowired private IDemoService demoService;
    //@AMAutowired("Service Class Name") private IDemoService demoService;

    @AMRequestMapping("/query")
    public void query(HttpServletRequest req, HttpServletResponse resp, @AMRequestParam String name){
        String result = demoService.get(name);
        try{
            resp.getWriter().write(result);
        }catch (IOException e){
            e.printStackTrace();
        }
    }
}
