package com.aemotionalline.infrastructure.retention;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Turns on the scheduled jobs (today only the retention period). */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class SchedulingConfiguration
{
}
