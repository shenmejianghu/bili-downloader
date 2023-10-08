package com.bilibili.downloader.util;



import javax.servlet.http.HttpServletResponse;
import java.io.*;

public class HttpFile {

    public static void downloadFile(String destFileName, String srcFilePath,
                                    HttpServletResponse response) {
        downloadFile("application/octet-stream",destFileName,srcFilePath,response);
    }

    public static void downloadFile(String contentType,String destFileName, String srcFilePath,
                                    HttpServletResponse response){
        try {
            destFileName = new String(destFileName.getBytes(), "ISO8859-1");
        } catch (UnsupportedEncodingException e) {
        }
        response.addHeader("Content-Disposition", "attachment; filename=" + destFileName);
        response.setContentType(contentType);
        try (FileInputStream fis = new FileInputStream(new File(srcFilePath));
             InputStream is = new BufferedInputStream(fis);
             BufferedOutputStream bos = new BufferedOutputStream(response.getOutputStream());) {

            byte[] buffer = new byte[2048];
            int read = -1;
            while ((read = is.read(buffer)) != -1) {
                bos.write(buffer, 0, read);
            }
            bos.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
