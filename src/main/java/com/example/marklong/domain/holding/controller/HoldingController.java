package com.example.marklong.domain.holding.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/holdings")
@Tag(name = "Holding", description = "보유 자산 정보 API")
public class HoldingController {
}
