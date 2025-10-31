package org.example.scheduled;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public class LogKeywordCounter {
    /**
     * 统计日志文件中关键字出现的次数
     *
     * @param keyword 要搜索的关键字
     * @return 出现的次数
     * @throws IOException 读取文件时的异常
     */
    public static int countKeyword(String keyword) throws IOException {
        // 获取当前项目路径
        String projectPath = System.getProperty("user.dir");
        File logFile = new File(projectPath, "log/myapp-20251031074159.log");

        if (!logFile.exists()) {
            throw new IOException("日志文件不存在: " + logFile.getAbsolutePath());
        }

        int count = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(logFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // 统计该行出现的次数
                int index = 0;
                while ((index = line.indexOf(keyword, index)) != -1) {
                    count++;
                    index += keyword.length();
                }
            }
        }

        return count;
    }

    // 示例用法
    public static void main(String[] args) {
        try {
            String keyword = "受理";
            int count = countKeyword(keyword);
            System.out.println("关键字 \"" + keyword + "\" 出现次数: " + count);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
