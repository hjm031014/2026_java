package com.nsu.team.communication.dto;

import java.util.List;

public record PagedItems<T>(List<T> items, PageInfo page) {}
