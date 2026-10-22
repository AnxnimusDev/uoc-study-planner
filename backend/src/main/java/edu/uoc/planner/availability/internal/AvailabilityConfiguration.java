package edu.uoc.planner.availability.internal;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AvailabilityProperties.class)
public class AvailabilityConfiguration {}
