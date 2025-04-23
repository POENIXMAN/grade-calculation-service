package ru.hpclab.hl.module1.Configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Setter
@Getter
@Component
@ConfigurationProperties(prefix = "school-journal")
public class SchoolJournalClientProperties {
    private String baseUrl;
    private Endpoints endpoints;

    @Setter
    @Getter
    public static class Endpoints {
        private String grades;
        private String subjects;
    }

}