package com.controleco.app.data

import java.time.YearMonth

/**
 * 账单/统计的统计周期：按月或按年。
 * 切换后所有"当前周期"数据流（流水、合计、分类占比、退款合计）都按对应区间重新查询。
 */
enum class PeriodMode(val label: String) {
    MONTH("月"),
    YEAR("年");

    fun canAdvanceFrom(selected: YearMonth, latest: YearMonth): Boolean =
        if (this == YEAR) selected.year < latest.year else selected < latest
}
