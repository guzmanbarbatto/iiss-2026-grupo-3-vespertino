package com.ioteste.core.domain;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Set;

// Representa la TarifaPunta del diagrama
public record TarifaPunta(LocalTime desde, LocalTime hasta, Set<DayOfWeek> dias) {}