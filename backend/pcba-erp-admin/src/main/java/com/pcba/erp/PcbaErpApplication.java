package com.pcba.erp;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * PCBA-ERP 启动类
 */
@SpringBootApplication(scanBasePackages = "com.pcba.erp")
@MapperScan({"com.pcba.erp.system.mapper", "com.pcba.erp.pdm.mapper", "com.pcba.erp.wms.mapper", "com.pcba.erp.pmm.mapper", "com.pcba.erp.scm.mapper", "com.pcba.erp.sal.mapper", "com.pcba.erp.assembly.mapper"})
@EnableAsync
@EnableScheduling
public class PcbaErpApplication {

    public static void main(String[] args) {
        SpringApplication.run(PcbaErpApplication.class, args);
        System.out.println("\n===============================================");
        System.out.println("   🚀 PCBA ERP 启动成功!");
        System.out.println("   后端接口:  http://localhost:8080/");
        System.out.println("   接口文档:  http://localhost:8080/doc.html");
        System.out.println("   健康检查:  http://localhost:8080/actuator/health");
        System.out.println("===============================================\n");
    }
}
