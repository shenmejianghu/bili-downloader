package com.bilibili.downloader.controller;

import cn.hutool.core.io.FileUtil;
import cn.hutool.crypto.digest.MD5;
import cn.hutool.http.HttpRequest;
import cn.hutool.json.JSON;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.bilibili.downloader.pojo.LiveConfig;
import com.bilibili.downloader.pojo.Result;
import com.bilibili.downloader.util.HttpFile;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.client.RequestCallback;
import org.springframework.web.client.ResponseExtractor;
import org.springframework.web.client.RestTemplate;

import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Controller
public class MainController {
    private static Logger logger = LoggerFactory.getLogger(MainController.class);

    @Autowired
    private RestTemplate restTemplate;
    @Value("${server.tomcat.basedir}")
    private String baseDir;
    @Value("${application.ffmpeg-path}")
    private String ffmpegPath;

    @RequestMapping("/download")
    public void download(HttpServletResponse response, String file){
        logger.info("下载视频文件：{}",file);
        if (StringUtils.isEmpty(file)){
            return;
        }
        String[] arr = file.split("_");
        if (arr.length != 2){
            return;
        }
        String filePath = baseDir+File.separator+arr[0]+File.separator+arr[1];
        if (!FileUtil.exist(filePath)){
            return;
        }
        HttpFile.downloadFile(arr[1],filePath,response);
        FileUtil.del(baseDir+File.separator+arr[0]);
    }

    @RequestMapping("/parse")
    @ResponseBody
    public Result<String> parse(String url){
        try {
            logger.info("开始解析视频地址：{}",url);
            String html = restTemplate.getForObject(url,String.class);
            String regex = "(?<=<script>window.__playinfo__=).*?(?=</script>)";
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(html);
            if (matcher.find()) {
                String uuid = UUID.randomUUID().toString().replace("-","");
                String dir = baseDir + File.separator + uuid +File.separator;
                if (!FileUtil.exist(dir)){
                    FileUtil.mkdir(dir);
                }
                String videoFile = "";
                String audioFile = "";
                String jsonStr = matcher.group();
                JSON json = JSONUtil.parse(jsonStr);
                JSONArray videoList = (JSONArray)json.getByPath("data.dash.video");
                JSONArray audioList = (JSONArray)json.getByPath("data.dash.audio");
                if (videoList != null){
                    for (Object video:videoList){
                        JSONObject map = (JSONObject)video;
                        String videoUrl = map.get("baseUrl").toString();
                        String segmentInit = map.getByPath("SegmentBase.Initialization").toString();
                        RequestCallback requestCallback = new RequestCallback() {
                            @Override
                            public void doWithRequest(ClientHttpRequest clientHttpRequest) throws IOException {
                                clientHttpRequest.getHeaders().add("Referer",url);
                                clientHttpRequest.getHeaders().add("User-Agent","Mozilla/5.0 (Macintosh; Intel Mac OS X 10_14_6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/80.0.3987.163 Safari/537.36");
                                clientHttpRequest.getHeaders().add("Range","bytes="+segmentInit);
                            }
                        };
                        ResponseExtractor responseExtractor = new ResponseExtractor<String>() {
                            @Override
                            public String extractData(ClientHttpResponse clientHttpResponse) throws IOException {
                                return clientHttpResponse.getHeaders().get("Content-Range").get(0).split("/")[1];
                            }
                        };
                        Object videoSize = restTemplate.execute(videoUrl, HttpMethod.GET,requestCallback,responseExtractor);
                        logger.info("视频地址：{}",videoUrl);
                        logger.info("视频大小：{}",videoSize);
                        RequestCallback videoRequestCallback = new RequestCallback() {
                            @Override
                            public void doWithRequest(ClientHttpRequest clientHttpRequest) throws IOException {
                                clientHttpRequest.getHeaders().add("Referer",url);
                                clientHttpRequest.getHeaders().add("User-Agent","Mozilla/5.0 (Macintosh; Intel Mac OS X 10_14_6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/80.0.3987.163 Safari/537.36");
                                clientHttpRequest.getHeaders().add("Range","bytes=0-"+videoSize);
                            }
                        };
                        String fileName = StringUtils.substringBefore(videoUrl,".m4s");
                        fileName = StringUtils.substringAfterLast(fileName,"/");
                        final String finalFileName = fileName +".mp4";
                        ResponseExtractor videoResponseExtractor = new ResponseExtractor<Boolean>() {
                            @Override
                            public Boolean extractData(ClientHttpResponse clientHttpResponse) throws IOException {
                                OutputStream output = null;
                                try {
                                    output = new FileOutputStream(dir+ finalFileName);
                                    logger.info("开始下载视频文件：{}",finalFileName);
                                    IOUtils.copy(clientHttpResponse.getBody(),output);
                                    logger.info("视频文件下载完成：{}",finalFileName);
                                    return Boolean.TRUE;
                                }catch (Exception e){
                                    e.printStackTrace();
                                    return Boolean.FALSE;
                                }finally {
                                    if (output != null){
                                        output.close();
                                    }
                                }
                            }
                        };
                        Object result = restTemplate.execute(videoUrl, HttpMethod.GET,videoRequestCallback,videoResponseExtractor);
                        if ((Boolean)result){
                            videoFile = finalFileName;
                            break;
                        }
                    }
                }
                if (audioList != null){
                    for (Object audio:audioList){
                        JSONObject map = (JSONObject)audio;
                        String audioUrl = map.get("baseUrl").toString();
                        String segmentInit = map.getByPath("SegmentBase.Initialization").toString();
                        RequestCallback requestCallback = new RequestCallback() {
                            @Override
                            public void doWithRequest(ClientHttpRequest clientHttpRequest) throws IOException {
                                clientHttpRequest.getHeaders().add("Referer",url);
                                clientHttpRequest.getHeaders().add("User-Agent","Mozilla/5.0 (Macintosh; Intel Mac OS X 10_14_6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/80.0.3987.163 Safari/537.36");
                                clientHttpRequest.getHeaders().add("Range","bytes="+segmentInit);
                            }
                        };
                        ResponseExtractor responseExtractor = new ResponseExtractor<String>() {
                            @Override
                            public String extractData(ClientHttpResponse clientHttpResponse) throws IOException {
                                return clientHttpResponse.getHeaders().get("Content-Range").get(0).split("/")[1];
                            }
                        };
                        Object audioSize = restTemplate.execute(audioUrl, HttpMethod.GET,requestCallback,responseExtractor);
                        logger.info("音频地址：{}",audioUrl);
                        logger.info("音频大小：{}",audioSize);
                        RequestCallback audioRequestCallback = new RequestCallback() {
                            @Override
                            public void doWithRequest(ClientHttpRequest clientHttpRequest) throws IOException {
                                clientHttpRequest.getHeaders().add("Referer",url);
                                clientHttpRequest.getHeaders().add("User-Agent","Mozilla/5.0 (Macintosh; Intel Mac OS X 10_14_6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/80.0.3987.163 Safari/537.36");
                                clientHttpRequest.getHeaders().add("Range","bytes=0-"+audioSize);
                            }
                        };
                        String fileName = StringUtils.substringBefore(audioUrl,".m4s");
                        fileName = StringUtils.substringAfterLast(fileName,"/");
                        final String finalFileName = fileName +".mp3";
                        ResponseExtractor audioResponseExtractor = new ResponseExtractor<Boolean>() {
                            @Override
                            public Boolean extractData(ClientHttpResponse clientHttpResponse) throws IOException {
                                OutputStream output = null;
                                try {
                                    output = new FileOutputStream(dir+ finalFileName);
                                    logger.info("开始下载音频文件：{}", finalFileName);
                                    IOUtils.copy(clientHttpResponse.getBody(),output);
                                    logger.info("音频文件下载完成：{}", finalFileName);
                                    return Boolean.TRUE;
                                }catch (Exception e){
                                    e.printStackTrace();
                                    return Boolean.FALSE;
                                }finally {
                                    if (output != null){
                                        output.close();
                                    }
                                }
                            }
                        };
                        Object result = restTemplate.execute(audioUrl, HttpMethod.GET,audioRequestCallback,audioResponseExtractor);
                        if ((Boolean)result){
                            audioFile = finalFileName;
                            break;
                        }
                    }
                }
                if (StringUtils.isEmpty(videoFile)&&StringUtils.isEmpty(audioFile)){
                    logger.warn("未找到视频：{}",url);
                    return Result.fail(null,"未找到视频");
                }
                if (StringUtils.isNotEmpty(videoFile) && StringUtils.isNotEmpty(audioFile)){
                    //ffmpeg -i 视频文件名.mp4 -i 音频文件名.mp3 -c:v copy -c:a copy 输出文件名.mp4
                    List<String> commands = new ArrayList<>();
                    commands.add(ffmpegPath);
                    commands.add("-i");
                    commands.add(dir+videoFile);
                    commands.add("-i");
                    commands.add(dir+audioFile);
                    commands.add("-c:v");
                    commands.add("copy");
                    commands.add("-c:a");
                    commands.add("copy");
                    commands.add(dir+"final-file.mp4");
                    logger.info("开始合成视频音频");
                    ProcessBuilder builder = new ProcessBuilder();
                    builder.command(commands);
                    try {
                        builder.inheritIO().start().waitFor();
                        logger.info("视频合成完成");
                    } catch (InterruptedException | IOException e) {
                        logger.info("视频合成失败：{}", ExceptionUtils.getStackTrace(e));
                    }
                    return Result.success(uuid+"_"+"final-file.mp4");
                }
                if (StringUtils.isNotEmpty(videoFile)){
                    return Result.success(uuid+"_"+videoFile);
                }
                if (StringUtils.isNotEmpty(audioFile)){
                    return Result.success(uuid+"_"+audioFile);
                }
                return Result.fail(null,"未找到视频");
            }else {
                logger.warn("视频地址解析错误");
                return Result.fail(null,"视频地址解析错误");
            }
        }catch (Exception e){
            logger.error(ExceptionUtils.getStackTrace(e));
            return Result.fail(null,"视频地址解析错误");
        }
    }
	
	@RequestMapping("/live")
    @ResponseBody
    public Result<String> live(@RequestBody LiveConfig live){
        List<String> commands = new ArrayList<>();
        commands.add(ffmpegPath);
        commands.add("-re");
        commands.add("-stream_loop");
        commands.add(live.getLoop().toString());
        commands.add("-i");
        commands.add(baseDir+"/test.mp4");
        commands.add("-vcodec");
        commands.add("copy");
        commands.add("-acodec");
        commands.add("copy");
        commands.add("-f");
        commands.add("flv");
        commands.add(live.getUrl()+live.getSecret());
        logger.info(StringUtils.join(commands," "));
        ProcessBuilder builder = new ProcessBuilder();
        builder.redirectErrorStream(true);
        builder.command(commands);
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                InputStream inputStream = null;
                try {
                    Process process = builder.start();
                    inputStream = process.getInputStream();
                    logger.info("开始推流");
                    BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        logger.info(line);
                    }
                    int result=process.waitFor();
                    logger.info("推流结果：{}",result);
                } catch (Exception e) {
                    logger.info("推流失败：{}", ExceptionUtils.getStackTrace(e));
                }finally {
                    if (inputStream != null){
                        try {
                            inputStream.close();
                        } catch (IOException e) {

                        }
                    }
                }
            }
        });
        thread.start();
        return Result.success(null);
    }
}
