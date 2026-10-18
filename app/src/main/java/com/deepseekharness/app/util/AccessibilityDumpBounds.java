package com.deepseekharness.app.util;

/**
 * 无障碍节点的矩形可用性判定。
 *
 * <p>零宽、零高的矩形是合法的：WebView 文本节点常报 {@code (70,139,1165,139)}，
 * 收起状态的侧滑抽屉常报 {@code (0,370,0,499)} —— 这些节点仍然有价值（能读出文字、
 * 能反映出还是没渲染）。真正不能用的是<b>反序矩形</b>（{@code right < left} 或
 * {@code bottom < top}）：横向分页视图被裁剪后常常留下这种残留，它四条边没有一条能用来
 * 定位，调用方拿它点屏幕只会点到别处。
 *
 * <p>主屏读屏（{@code DshaAccessibilityService.dumpNode}）和虚拟屏树
 * （{@code VirtualScreenAccessibility.walk}）必须共用同一条不变量：两边各写一份时，
 * 只有一边会跟着修（issue #86 / #143）。
 */
public final class AccessibilityDumpBounds {
    private AccessibilityDumpBounds() { }

    /** 矩形是否有序：允许零面积，禁止反序。 */
    public static boolean ordered(int left, int top, int right, int bottom) {
        return right >= left && bottom >= top;
    }

    /**
     * 节点是否值得报给调用方：有可读文字或可操作能力，且矩形能用来定位。
     *
     * @param labeled    是否有文字或描述
     * @param actionable 是否可点、可编辑或可滚动（各调用方按自己的语义给定）
     */
    public static boolean reportable(boolean labeled, boolean actionable,
            int left, int top, int right, int bottom) {
        return (labeled || actionable) && ordered(left, top, right, bottom);
    }
}
