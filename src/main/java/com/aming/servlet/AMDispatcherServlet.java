package com.aming.servlet;

import com.aming.annotation.AMAutowired;
import com.aming.annotation.AMController;
import com.aming.annotation.AMRequestMapping;
import com.aming.annotation.AMService;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;

/**
 * Author: Aming 2019
 */
public class AMDispatcherServlet extends HttpServlet {

    // 全局变量 放置配置文件的key value
    private Properties contextConfig = new Properties();

    // 放置扫描包后得到类名
    private List<String> classNames = new ArrayList<String>();

    // 典型的注册式单例 容器
    private HashMap<String, Object> ioc = new HashMap<String, Object>();

    // 放置Url和 Controller类的方法的key value映射集合
    private Map<String, Method> handlerMapping = new HashMap<String, Method>();


    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.doPost(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            System.out.println("doPost running");
            doDispatch(req, resp);
        } catch (Exception e) {
            e.printStackTrace();
            resp.getWriter()
                    .write("500 Exception: Details:\r\n" + Arrays.toString(e.getStackTrace())
                            .replaceAll("\\[|\\]", "")
                            .replaceAll(",\\s", "\r\n"));
        }
    }

    private void doDispatch(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        String url = req.getRequestURI();
        System.out.println("url: " +url);
        String contextPath = req.getContextPath();
        System.out.println("contextPath: " + contextPath);

        //从底层统一url的规则，使用相对路径
        url = url.replace(contextPath,"").replaceAll("/+", "/");
        System.out.println("url: " + url);
        if(!this.handlerMapping.containsKey(url)){
            resp.getWriter().write("404 Not Found!");
            return;
        }
        Method method = this.handlerMapping.get(url);
        // 获取方法的参数列表
        Class<?> [] parameterTypes = method.getParameterTypes();
        // 获取请求req的参数
        Map<String,String[]>  parameterMap =req.getParameterMap();
        //保存参数值
        Object [] paramValues = new Object[parameterTypes.length];
        // 方法的参数列表
        for (int i = 0; i < parameterTypes.length ; i++) {
            //根据参数做某些处理
            Class parameterType = parameterTypes[i];
            if(parameterType == HttpServletRequest.class){
                // 参数已明确，强制类型
                paramValues[i] = req;
                continue;
            }else if(parameterType == HttpServletResponse.class){
                paramValues[i] =resp;
                continue;
            }else if(parameterType == String.class){
                for (Map.Entry<String, String[]> param : parameterMap.entrySet()) {
                    System.out.println("Arrays.toString(param.getValue()): " + Arrays.toString(param.getValue()));
                    //
                    System.out.println(param.getKey()); // name age
                    // 如果提交参数时有参数名相同（param.getkey()），value则为多元素数组。
                    // 如果提交参数时有参数名唯一（param.getkey()），value则为单元素数组。
                    // 这里controller中设计业务方法时，可以将Map作为第三个参数，使用一个公共方法来解析这个map，得到客户端提交的各个参数。
                    // 这里用String 作为第三个参数只是为了简单，但实际情况要复杂一些。
                    String value = Arrays.toString(param.getValue())
                            // 去掉参数数组的字符串形式的方括号和空格 变为参数值间隔逗号形式
                            .replaceAll("\\[|\\]", "")
                            .replaceAll(",\\s", ",");
                    System.out.println("Request params：" + value);
                    paramValues[i] = value;
                }
            }
        }
        // 取得方法的实例的技巧
        String beanName = toLowerFirsCase(method.getDeclaringClass().getSimpleName());
        // [Ljava.lang.String; params.get("name")是数组
        // http://localhost:8080/mySpring_war/demo/query?name=alex
        // method.invoke(ioc.get(beanName), new Object[]{req,resp, parameterMap.get("name")[0]});
        // 利用反射机制来调用
        // Request req, Reponse resp, String s
        System.out.println(String.format("Array.toString(paramValues) = ", Arrays.toString(paramValues)));
        System.out.println(String.format("beanName:%s, paramValues:%s", beanName, paramValues));
        System.out.println(String.format("ioc %s", ioc));
        method.invoke(ioc.get(beanName), paramValues);


    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        // 1. 加载配置文件
        doLoadConfig(config.getInitParameter("contextConfigLocation"));
        // 2. 扫描所有相关的类
        doSanner(contextConfig.getProperty("scanPackage"));
        // 3. 初始化IoC容器，并且将扫描出来的类的实例存放在IoC容器当中
        doInstance();
        // 4. 完成依赖注入
        doAutowired();
        // 5. 初始化 HandlerMapping
        initHandlerMapping();
        System.out.println("AM Mini Spring is init");
    }

    private void initHandlerMapping() {
        if (ioc.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
            Class<?> clazz = entry.getValue()
                    .getClass();
            if (!clazz.isAnnotationPresent(AMController.class)) {
                continue;
            }
            String baseUrl = "";
            // 获取Controller url位置 @AMRequestMapping("/demo")
            if (clazz.isAnnotationPresent(AMRequestMapping.class)) {
                AMRequestMapping requestMapping = clazz.getAnnotation(AMRequestMapping.class);
                baseUrl = requestMapping.value();
            }
            ;

            // 获取的类的公共方法; 获取所有方法 clazz.getDeclaredMethods()
            Method[] methods = clazz.getMethods();
            for (Method method : methods) {
                // 没有加注解的直接忽略
                if (!method.isAnnotationPresent(AMRequestMapping.class)) {
                    continue;
                }

                // 映射Url
                AMRequestMapping amRequestMapping = method.getAnnotation(AMRequestMapping.class);
                //  @AMRequestMapping("/demo")    @AMRequestMapping("demo") 有些人不加斜杠
                //  @AMRequestMapping("/query")  @AMRequestMapping("query")
                // //demo//query   demoquery
                // 如果有多余斜杠替换一个斜杠
                String url = ("/" + baseUrl + "/" + amRequestMapping.value()).replaceAll("/+", "/");
                handlerMapping.put(url, method);
                System.out.println("Map " + url + "," + method);

            }

        }

    }

    private void doAutowired() {
        if (ioc.isEmpty()) return;
        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
            // 取出加了注解的字段
            Field[] fields = entry.getValue()
                    .getClass()
                    .getDeclaredFields();
            for (Field field : fields) {
                if (!field.isAnnotationPresent(AMAutowired.class)) {
                    continue;
                }
                ;
                AMAutowired autowired = field.getAnnotation(AMAutowired.class);
                String beanName = autowired.value();
                if ("".equals(beanName)) {
                    // 如果没有自定义名字 like @AMAutowired(beanName)
                    beanName = field.getType()
                            .getName();
                }

                // 不是面向对象的方法，而是反射的方法，但field是私有的，需要授权才能访问
                // 暴力访问，反射机制
                field.setAccessible(true);
                try {
                    field.set(entry.getValue(), ioc.get(beanName));
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }

            }
        }
    }

    private void doInstance() {
        if (classNames.isEmpty()) return;
        try {
            for (String className : classNames) {
                Class<?> clazz = Class.forName(className);
                // 下一步，初始化，并 保存到容器中
                if (clazz.isAnnotationPresent(AMController.class)) {
                    Object instance = clazz.newInstance();
                    String beanName = toLowerFirsCase(clazz.getSimpleName());
                    ioc.put(beanName, instance);
                } else if (clazz.isAnnotationPresent(AMService.class)) {
                    String beanName = toLowerFirsCase(clazz.getSimpleName());
                    // 得到一个当前类的注解AMservice的实例
                    AMService service = clazz.getAnnotation(AMService.class);
                    if (!"".equals(service.Value()
                            .trim())) {
                        beanName = service.Value();
                    }
                    Object instance = clazz.newInstance();
                    ioc.put(beanName, instance);
                    // 类型的全名 com.aming.demo.service.IDemoService
                    for (Class<?> i : clazz.getInterfaces()) {
                        // 技巧：如果一个类实现了多个接口，获取该实现类的所有接口的类全名

                        if (ioc.containsKey(i.getName())) {
                            // 有时会有多个类实现了同一个服务接口，如果注释有对服务实现类取一个定义命名，则没有问题。
                            // 如果没有: @AMServie() Spring IoC容器中一个bean命名多个实例，抛出错误。
                            throw new Exception("The beanNAME \"" + i.getName() + "\" is exists");
                        }
                        ioc.put(i.getName(), instance);
                    }

                }
                ;

            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 一句户首字母变小写
    private String toLowerFirsCase(String simpleName) {
        char[] chars = simpleName.toCharArray();
        chars[0] += 32;
        return String.valueOf(chars);
    }

    // 递归方式
    private void doSanner(String scanPackage) {
        URL url = this.getClass()
                .getClassLoader()
                .getResource("/" + scanPackage.replaceAll("\\.", "/"));
        File classDir = new File(url.getFile());
        for (File file : classDir.listFiles()) {
            if (file.isDirectory()) {
                doSanner(scanPackage + "." + file.getName());
            } else {
                if (!file.getName()
                        .endsWith(".class")) continue;
                String className = scanPackage + "." + file.getName()
                        .replace(".class", "");
                classNames.add(className);
            }

        }
    }

    private void doLoadConfig(String contextConfigLocation) {
        System.out.println(contextConfigLocation);
        // contextConfigLocation is just a String to the file path.
        InputStream fis = null;
        try {
            fis = this.getClass()
                    .getClassLoader()
                    .getResourceAsStream(contextConfigLocation);
            // 1. 读取配置文件
            contextConfig.load(fis);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (null != fis) {
                    fis.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
    }
}
