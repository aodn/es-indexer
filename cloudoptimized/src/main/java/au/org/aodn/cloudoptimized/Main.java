package au.org.aodn.cloudoptimized;

import au.org.aodn.cloudoptimized.service.au.org.aodn.cloudoptmized.ParquetReader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.IOException;
import java.time.ZonedDateTime;

@Slf4j
@SpringBootApplication
public class Main implements CommandLineRunner {

    @Autowired
    ParquetReader parquetReader;
    public static void main(String[] args) {
        log.info("Start testing");
        SpringApplication.run(Main.class, args);
        log.info("End testing");
    }

    @Override
    public void run(String... args) {
        log.info("Find Max Time {}", parquetReader.getDatasetMaxTimestamp("anmn_ctd_ts_fv01"));

        log.info("Subset dataset");
        parquetReader.getDatasetFromRange("anmn_ctd_ts_fv01", "GBROTE", ZonedDateTime.now().minusMonths(10), ZonedDateTime.now());
    }
}
