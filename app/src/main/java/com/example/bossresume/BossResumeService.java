package com.example.bossresume;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.view.inputmethod.EditorInfo;
import android.content.pm.PackageManager;
import android.util.DisplayMetrics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class BossResumeService extends AccessibilityService {

    private static final String TAG = "BossResumeService";
    public static final String ACTION_START = "com.example.bossresume.ACTION_START";
    public static final String ACTION_STOP = "com.example.bossresume.ACTION_STOP";
    
    // 添加常量定义
    private static final int SCROLL_COOLDOWN = 1000; // 滑动冷却时间1秒
    
    private boolean isRunning = false;
    private int totalCount = 0;
    private int maxCount = 150;
    private Handler handler = new Handler(Looper.getMainLooper());
    
    // 添加打招呼计数器
    private int greetingCount = 0;
    private static final int MAX_GREETING_COUNT = 300; // 将最大打招呼次数设为300
    private boolean isServiceStopping = false; // 添加标记，避免重复停止
    private boolean dailyLimitReached = false; // 标记是否达到每日限制
    
    // 关键词列表 - 动态获取
    private List<String> keywords = new ArrayList<>();
    
    // 已点击的节点记录，避免重复点击
    private List<String> clickedNodes = new ArrayList<>();
    
    // 当前状态
    private enum State {
        BROWSING_LIST,    // 浏览职位列表
        VIEWING_DETAIL,   // 查看职位详情
        COMMUNICATING     // 沟通/投递中
    }
    
    private State currentState = State.BROWSING_LIST;

    // 界面类型
    private enum PageType {
        MAIN_LIST,       // 主界面职位列表（有"推荐"、"附近"、"最新"、"职位"等文字）
        JOB_DETAIL,      // 职位详情页面（有"职位详情"、"立即沟通"或"继续沟通"按钮）
        CHAT_PAGE        // 聊天页面（有聊天输入框、发送按钮等）
    }

    // 添加返回操作计数器和时间戳，用于控制返回频率
    private int backOperationCount = 0;
    private long lastBackTime = 0;

    // 添加类级别的静态变量
    private static long lastStateChangeTime = 0;

    // 添加页面加载等待时间常量
    private static final int DETAIL_PAGE_LOAD_DELAY = 8000; // 职位详情页面加载等待时间
    private static final int MAIN_PAGE_LOAD_DELAY = 1000;   // 主页面加载等待时间
    private static final int CHAT_PAGE_LOAD_DELAY = 8000;   // 聊天页面加载等待时间
    private static final int BACK_OPERATION_DELAY = 1000;   // 返回操作之间的等待时间
    private static final int PAGE_TRANSITION_DELAY = 5000;  // 页面切换后的等待时间

    // 添加最大返回操作次数限制
    private static final int MAX_BACK_OPERATIONS = 10; // 单次会话最大返回操作次数
    private int totalBackOperations = 0; // 记录总返回操作次数
    private long sessionStartTime = 0; // 会话开始时间

    // 添加更严格的返回操作控制
    private static final int MAX_CONSECUTIVE_BACKS = 1; // 最大连续返回次数，降低为1避免多次返回
    private int consecutiveBackCount = 0; // 连续返回计数
    private long lastSuccessfulOperation = 0; // 上次成功操作时间（非返回操作）

    // 添加页面特定的最大返回次数限制
    private static final int MAX_BACKS_MAIN_PAGE = 0;     // 主界面最大返回次数
    private static final int MAX_BACKS_DETAIL_PAGE = 1;   // 职位详情页最大返回次数
    private static final int MAX_BACKS_CHAT_PAGE = 2;     // 聊天页面最大返回次数

    // 添加打招呼相关常量和变量
    private static final String GREETING_MESSAGE = "您好"; // 打招呼用语
    private boolean greetingSent = false; // 标记是否已发送打招呼消息
    private boolean greetingDetected = false; // 标记是否检测到已发送的打招呼消息

    // 添加一个变量跟踪当前界面层级
    private PageType currentPageType = null;
    private PageType previousPageType = null;
    
    // 添加屏幕尺寸变量
    private int screenWidth = 0;
    private int screenHeight = 0;

    // 添加滑动状态跟踪变量
    private long lastScrollTime = 0;
    private boolean isScrolling = false;
    

    // 添加BOSS直聘包名和启动Activity常量
    private static final String BOSS_PACKAGE_NAME = "com.hpbr.bosszhipin";
    private static final String BOSS_MAIN_ACTIVITY = "com.hpbr.bosszhipin.module.launcher.WelcomeActivity";
    private static final int APP_OPERATION_DELAY = 2000; // 操作延迟时间
    private boolean appExitDetected = false; // 标记是否检测到APP退出
    
    // 添加状态检查计时器相关变量
    private Handler appStatusCheckHandler = new Handler(Looper.getMainLooper());
    private Runnable appStatusCheckRunnable;
    private static final long APP_STATUS_CHECK_INTERVAL = 5000; // 每5秒检查一次APP状态
    
    // 添加跟踪任务的变量
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private List<Runnable> pendingTasks = new ArrayList<>();
    
    // 添加返回操作间隔时间检查变量
    private long lastBackOperationTime = 0;
    private static final long MIN_BACK_INTERVAL = 3000; // 两次返回操作之间的最小间隔时间（3秒）
    


    // 添加职位标签点击时间控制变量
    private long lastPositionTabClickTime = 0;
    private static final long MIN_TAB_CLICK_INTERVAL = 2000; // 最小点击间隔2秒
    private boolean isTabClickPending = false; // 标记是否有待执行的标签点击

    // 获取当前界面类型 - 修复中文引号导致的编译错误
    private PageType getCurrentPageType(AccessibilityNodeInfo rootNode) {
        if (rootNode == null) return null;
        
        // 首先检查是否在职位列表页面 - 这是最优先的检查
        // 查找职位标签是否被选中
        List<AccessibilityNodeInfo> tabNodes = rootNode.findAccessibilityNodeInfosByViewId("com.hpbr.bosszhipin:id/tv_tab_1");
        for (AccessibilityNodeInfo node : tabNodes) {
            if (node != null && node.isSelected() && node.getText() != null && 
                "职位".equals(node.getText().toString())) {
                logMessage("检测到职位标签被选中，判定为职位主界面");
                return PageType.MAIN_LIST;
            }
        }
        
        // 检查是否存在"再按一次退出程序"提示，这也表明在主界面
        List<AccessibilityNodeInfo> exitPromptNodes = rootNode.findAccessibilityNodeInfosByText("再按一次退出程序");
        if (!exitPromptNodes.isEmpty()) {
            logMessage("检测到\"再按一次退出程序\"提示，判定为职位主界面");
            return PageType.MAIN_LIST;
        }
        
        // 其次检查是否在职位详情页面
        List<AccessibilityNodeInfo> chatButtons = rootNode.findAccessibilityNodeInfosByViewId("com.hpbr.bosszhipin:id/btn_chat");
        for (AccessibilityNodeInfo button : chatButtons) {
            if (button != null && button.getText() != null) {
                String buttonText = button.getText().toString();
                if ("立即沟通".equals(buttonText) || "继续沟通".equals(buttonText)) {
                    logMessage("检测到\"" + buttonText + "\"按钮，判定为职位详情页");
                    return PageType.JOB_DETAIL;
                }
            }
        }
        
        // 最后检查是否在聊天页面
        List<AccessibilityNodeInfo> chatFeatures = rootNode.findAccessibilityNodeInfosByViewId("com.hpbr.bosszhipin:id/mTextView");
        for (AccessibilityNodeInfo feature : chatFeatures) {
            if (feature != null && feature.getText() != null) {
                String featureText = feature.getText().toString();
                if ("发简历".equals(featureText) || 
                    "换电话".equals(featureText) || 
                    "换微信".equals(featureText) || 
                    "不感兴趣".equals(featureText)) {
                    logMessage("检测到\"" + featureText + "\"，判定为聊天页面");
            return PageType.CHAT_PAGE;
        }
            }
        }
        
        // 如果前一个页面是职位列表页面，且当前处于FrameLayout（可能是退出提示），仍判断为职位列表
        if (previousPageType == PageType.MAIN_LIST && 
            rootNode.getClassName() != null && 
            "android.widget.FrameLayout".equals(rootNode.getClassName().toString())) {
            logMessage("检测到从职位列表进入FrameLayout，可能是退出提示，仍判定为职位列表");
            return PageType.MAIN_LIST;
        }
        
        logMessage("未能识别当前界面类型，className: " + 
                  (rootNode.getClassName() != null ? rootNode.getClassName() : "null"));
        return null;
    }

   

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // 如果服务正在停止或未运行，直接返回，不处理任何事件
        if (isServiceStopping || !isRunning) {
            return;
        }
        
        // 记录窗口状态变化，更好地检测弹窗
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            String packageName = event.getPackageName() != null ? event.getPackageName().toString() : "未知包名";
            String className = event.getClassName() != null ? event.getClassName().toString() : "未知类名";
            
            logMessage("窗口状态变化: " + packageName + " - " + className);
            
            // 更新窗口状态变化时间
            lastStateChangeTime = System.currentTimeMillis();
            
            // 如果离开了职位详情页，重置计时器
            if (!className.contains("JobDetailActivity")) {
                lastDetailPageTime = 0;
            }
            
            // 专门检测聊天界面中的弹窗
            if (BOSS_PACKAGE_NAME.equals(packageName) && 
                className.contains("ChatRoomActivity")) {
                logMessage("检测到进入聊天页面");
                
                // 重置沟通点击状态
                hasCommunicateClicked = false;
                lastDetailPageTime = 0; // 重置详情页计时器
            }
        }

        // 首先检查是否显示了聊天限制提示，这是最高优先级检查
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode != null) {
            // 使用部分匹配而不是完全匹配，以处理可能的变体文本
            if (checkDailyLimitReached(rootNode)) {
                logMessage("全局检测到聊天限制提示，准备返回主页面并停止服务");
                rootNode.recycle();
                
                // 使用更精确的处理方法
                handleDailyLimitReached();
                
                // 延迟后停止服务
        handler.postDelayed(() -> {
                    if (dailyLimitReached) {
                        logMessage("已达到每日聊天限制，准备停止服务");
                        stopService();
                    }
                }, 5000);  // 增加延迟，确保有足够时间返回到职位界面
                    return;
                }
                
            // 如果已经达到限制但仍在运行，强制返回并尝试停止
            if (dailyLimitReached) {
                logMessage("已达到每日限制，但服务仍在运行，强制返回并停止");
                performBackOperation();
                handler.postDelayed(this::stopService, 2000);
                rootNode.recycle();
                    return;
            }
        }

        // 更新最后一次用户交互时间
        if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_CLICKED ||
            event.getEventType() == AccessibilityEvent.TYPE_VIEW_LONG_CLICKED ||
            event.getEventType() == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            lastUserInteractionTime = System.currentTimeMillis();
        }
            
            // 获取当前页面类型
        if (rootNode != null) {
            PageType currentPage = getCurrentPageType(rootNode);
            if (currentPage != null) {
                handlePageByType(currentPage, rootNode);
            }
            rootNode.recycle();
        }

        // 专门监听Toast提示（可能不在UI树中）
        if (event.getEventType() == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            if (event.getText() != null && !event.getText().isEmpty()) {
                for (CharSequence text : event.getText()) {
                    if (text != null && text.toString().contains("今日聊得太多")) {
                        logMessage("检测到Toast通知: " + text);
                        handleDailyLimitReached();
                        return;
                    }
                }
            }
        }

        // 监听内容变化事件，专门用于检测弹窗
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED 
            && BOSS_PACKAGE_NAME.equals(event.getPackageName())) {
            // 仅在聊天页面检测内容变化
            if (event.getClassName() != null && 
                event.getClassName().toString().contains("ChatRoomActivity")) {
                // 获取变化的节点
                AccessibilityNodeInfo source = event.getSource();
                if (source != null) {
                    findChatLimitText(source);
                    source.recycle();
                }
            }
        }

        // 在处理页面切换事件时，更新状态切换时间
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || 
            event.getEventType() == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            
            AccessibilityNodeInfo updatedRootNode = getRootInActiveWindow();  // 修改变量名，避免冲突
            if (updatedRootNode != null) {
                PageType newPageType = getCurrentPageType(updatedRootNode);
                
                // 如果检测到页面类型变化，更新状态切换时间
                if (newPageType != currentPageType) {
                    logMessage("页面类型从 " + currentPageType + " 变为 " + newPageType);
                    previousPageType = currentPageType;
                    currentPageType = newPageType;
                    lastStateChangeTime = System.currentTimeMillis();  // 更新状态变化时间
                    
                    // 如果从列表页面进入详情页面，特别标记
                    if (previousPageType == PageType.MAIN_LIST && newPageType == PageType.JOB_DETAIL) {
                        currentState = State.VIEWING_DETAIL;
                        logMessage("从列表页面进入详情页面，状态更新为查看详情");
                    }
                }
                
                updatedRootNode.recycle();  // 修改变量名，避免冲突
            }
        }
    }

    // 专门用于检测聊天页面中的限制文本
    private void findChatLimitText(AccessibilityNodeInfo node) {
        if (node == null) return;
        
        // 检查当前节点是否包含目标文本
        if (node.getText() != null) {
            String text = node.getText().toString();
            // 使用更宽松的匹配条件，增加检测成功率
            if (text.contains("太多") || 
                text.contains("明天") || 
                text.contains("休息") || 
                (text.contains("聊天") && text.contains("限制"))) {
                logMessage("检测到每日聊天限制: " + text);
                // 标记已达到限制并处理
                dailyLimitReached = true;
                handler.postDelayed(this::handleDailyLimitReached, 500);
                    return;
                }
        }
        
        // 递归检查所有子节点
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                findChatLimitText(child);
                child.recycle();
            }
        }
    }

    private void findAndClickJobs(AccessibilityNodeInfo rootNode) {
        if (rootNode == null) return;
        
        // 检查是否正在滑动或滑动冷却期内
        if (isScrolling || (System.currentTimeMillis() - lastScrollTime < SCROLL_COOLDOWN)) {
            logMessage("正在滑动中或滑动冷却期内，跳过此次操作");
            return;
        }
        
        // 获取当前页面类型
        PageType currentPage = getCurrentPageType(rootNode);
        
        // 如果不在主界面，尝试返回主界面
        if (currentPage != null && currentPage != PageType.MAIN_LIST) {
            logMessage("当前不在主界面，尝试返回主界面");
            performDoubleBackToMainPage();
            return;
        }
        
        // 查找符合条件的职位节点
        List<AccessibilityNodeInfo> jobNodes = findJobNodes(rootNode);
        
        // 如果找到符合条件的职位节点，点击第一个
        if (!jobNodes.isEmpty()) {
            AccessibilityNodeInfo jobNode = jobNodes.get(0);
            String jobNodeText = getNodeText(jobNode);
            
            // 检查是否已点击过该节点
            if (clickedNodes.contains(jobNodeText)) {
                logMessage("该职位已点击过，尝试滑动查找新职位");
                scrollDown();
                return;
            }
            
            // 记录已点击的节点
            clickedNodes.add(jobNodeText);
            
            // 点击职位节点
            logMessage("点击职位: " + jobNodeText);
            clickNode(jobNode);
            
            // 更新状态
            currentState = State.VIEWING_DETAIL;
            
            // 增加总计数
            totalCount++;
            logMessage("当前已处理职位数: " + totalCount + "/" + maxCount);
            
            // 检查是否达到最大处理数量
            if (totalCount >= maxCount) {
                logMessage("已达到最大处理数量，停止服务");
                stopService();
                return;
            }
            
            // 不再需要延迟检查，直接等待页面变化事件
            return;
        }
        
        // 如果没有找到符合条件的职位节点，滑动查找更多职位
        logMessage("未找到符合条件的职位，滑动查找更多");
        scrollDown();
    }
    
    // 修改查找职位卡片的方法，添加关键词匹配日志
    private List<AccessibilityNodeInfo> findJobCards(AccessibilityNodeInfo rootNode) {
        List<AccessibilityNodeInfo> jobCardList = new ArrayList<>();
        if (rootNode == null) return jobCardList;
        
        try {
            // 首先尝试通过特定ID查找职位卡片
            List<AccessibilityNodeInfo> jobCards = rootNode.findAccessibilityNodeInfosByViewId("com.hpbr.bosszhipin:id/view_job_card");
            
            if (!jobCards.isEmpty()) {
                logMessage("找到 " + jobCards.size() + " 个职位卡片");
                
                for (AccessibilityNodeInfo jobCard : jobCards) {
                    // 获取职位卡片中的文本信息
                    String jobTitle = getTextFromNode(jobCard);
                    
                    if (jobTitle != null && !jobTitle.isEmpty()) {
                        // 添加职位信息日志，不再在这里显示关键词列表
                        logMessage("检查职位: " + jobTitle);
                        
                        // 添加关键词匹配检查
                        boolean matched = false;
                        List<String> matchedKeywords = new ArrayList<>();
                        if (keywords != null && !keywords.isEmpty()) {
                            for (String keyword : keywords) {
                                if (!keyword.isEmpty() && jobTitle.toLowerCase().contains(keyword.toLowerCase())) {
                                    // 收集匹配的关键词，稍后一次性输出
                                    matchedKeywords.add(keyword);
                                    matched = true;
                                }
                            }
                        }
                        
                        // 输出匹配结果
                        if (matched) {
                            logMessage("✓ 匹配成功！在职位中找到关键词: " + matchedKeywords);
                            // 检查该节点是否已被点击过
                            if (!clickedNodes.contains(jobTitle)) {
                                jobCardList.add(jobCard);
                            } else {
                                logMessage("但该职位已点击过，跳过");
                            }
                        } else {
                            if (!keywords.isEmpty() && !keywords.get(0).isEmpty()) {
                                logMessage("✗ 未在职位中匹配到任何关键词，跳过该职位");
                            }
                        }
                    }
                }
            } else {
                // 如果找不到特定ID的职位卡片，尝试其他方法识别职位列表项
                logMessage("未找到职位卡片，请确认是否在职位列表页面");
            }
            
            return jobCardList;
        } catch (Exception e) {
            logMessage("查找职位卡片时发生错误: " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    // 根据类名查找节点
    private List<AccessibilityNodeInfo> findNodesByClassName(AccessibilityNodeInfo rootNode, String className) {
        List<AccessibilityNodeInfo> result = new ArrayList<>();
        if (rootNode == null) return result;
        
        // 检查当前节点
        if (rootNode.getClassName() != null && rootNode.getClassName().toString().equals(className)) {
            result.add(rootNode);
        }
        
        // 递归检查子节点
        for (int i = 0; i < rootNode.getChildCount(); i++) {
            AccessibilityNodeInfo child = rootNode.getChild(i);
            if (child != null) {
                result.addAll(findNodesByClassName(child, className));
            }
        }
        
        return result;
    }
    
    // 检查节点是否包含多个文本节点（职位卡片的特征）
    private boolean containsMultipleTextNodes(AccessibilityNodeInfo node) {
        int textNodeCount = 0;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null && child.getText() != null) {
                textNodeCount++;
                if (textNodeCount >= 2) {
                    return true;
                }
            }
        }
        return false;
    }
    
    // 获取节点中的所有文本
    private List<String> getAllTextsFromNode(AccessibilityNodeInfo node) {
        List<String> texts = new ArrayList<>();
        if (node == null) return texts;
        
        if (node.getText() != null) {
            texts.add(node.getText().toString());
        }
        
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                texts.addAll(getAllTextsFromNode(child));
            }
        }
        
        return texts;
    }
    
    private String getNodeIdentifier(AccessibilityNodeInfo node) {
        if (node == null) return "";
        
        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        String text = node.getText() != null ? node.getText().toString() : "";
        
        return text + "_" + bounds.toString();
    }
    
    private void clickNode(AccessibilityNodeInfo node) {
        if (node == null) return;
        
        // 尝试直接点击
        if (node.isClickable()) {
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            return;
        }
        
        // 如果节点不可点击，尝试查找可点击的父节点
        AccessibilityNodeInfo parent = node;
        while (parent != null) {
            if (parent.isClickable()) {
                parent.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                return;
            }
            parent = parent.getParent();
        }
        
        // 如果没有可点击的父节点，使用手势点击
        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        clickAtPosition(bounds.centerX(), bounds.centerY());
    }
    
    private void scrollScreen() {
        logMessage("执行屏幕滑动");
        isScrolling = true;
        lastScrollTime = System.currentTimeMillis();
        
        // 创建滑动路径
        Path path = new Path();
        path.moveTo(screenWidth / 2, screenHeight * 0.8f);
        path.lineTo(screenWidth / 2, screenHeight * 0.2f);
        
        // 创建手势描述
        GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
        gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 0, 500));
        
        // 执行手势
        dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                super.onCompleted(gestureDescription);
                logMessage("滑动手势完成");
                // 延迟后重置滑动状态
                handler.postDelayed(() -> {
                    isScrolling = false;
                }, 1000);
            }
            
            @Override
            public void onCancelled(GestureDescription gestureDescription) {
                super.onCancelled(gestureDescription);
                logMessage("滑动手势被取消");
                isScrolling = false;
            }
        }, null);
    }
    
    private void clickAtPosition(float x, float y) {
        Path path = new Path();
        path.moveTo(x, y);
        
        GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
        gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 0, 100));
        
        dispatchGesture(gestureBuilder.build(), null, null);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (action != null) {
                switch (action) {
                    case ACTION_START:
                        // 只有在未运行且未停止时才启动
                        if (!isRunning && !isServiceStopping) {
                            isRunning = true;
                            isServiceStopping = false;
                            
                            // 先获取关键词
                            SharedPreferences sharedPreferences = getSharedPreferences("app_settings", Context.MODE_PRIVATE);
                            String keywordsString = sharedPreferences.getString("keywords", "运维,docker,k8s,系统运维,集群运维,kubernetes,devops");
                            logMessage("从SharedPreferences获取到的原始关键词字符串: " + keywordsString);
                            
                            // 使用默认关键词，确保不会为空
                            if (keywordsString.isEmpty()) {
                                keywordsString = "运维,docker,k8s,系统运维,集群运维,kubernetes,devops";
                                logMessage("使用默认关键词: " + keywordsString);
                            }
                            
                            String[] keywordArray = keywordsString.split(",");
                            
                            // 使用ArrayList而不是Arrays.asList，因为后者返回的是固定大小的不可修改列表
                            keywords = new ArrayList<>(Arrays.asList(keywordArray));
                            
                            // 清理空字符串
                            keywords.removeIf(String::isEmpty);
                            
                            logMessage("转换后的关键词列表: " + keywords);
                            
                            if (keywords.isEmpty() || (keywords.size() == 1 && keywords.get(0).isEmpty())) {
                                logMessage("警告：关键词列表为空或只包含空字符串");
                            }
                            
                            // 启动BOSS直聘应用
                            Intent launchIntent = getPackageManager().getLaunchIntentForPackage(BOSS_PACKAGE_NAME);
                            if (launchIntent != null) {
                                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                startActivity(launchIntent);
                                logMessage("启动BOSS直聘应用");
                            } else {
                                logMessage("未能获取BOSS直聘启动Intent，请确认应用已安装");
                                return START_NOT_STICKY;
                            }
                            
                            // 初始化或重置一些状态变量
            totalCount = 0;
            greetingCount = 0;
            clickedNodes.clear();
            currentState = State.BROWSING_LIST;
            
                            // 开始监控任务
                            startServiceWatchdog();
                            startAppStatusCheck();
                            
                            // 发送广播通知MainActivity更新UI
                            Intent statusIntent = new Intent(MainActivity.ACTION_SERVICE_STATUS_CHANGED);
                            statusIntent.putExtra("running", true);
                            sendBroadcast(statusIntent);
                            
                            logMessage("服务已启动，当前使用的关键词列表: " + keywords);
                        }
                        break;
                    case ACTION_STOP:
                        // 停止服务
                        if (isRunning) {
                            stopService();
                        }
                        break;
                }
            }
        }
        
        // 启动界面检测
        checkHandler.post(pageCheckRunnable);
        
        return START_NOT_STICKY;  // 改为NOT_STICKY，避免系统自动重启服务
    }

    // 修改startService方法，接收关键词参数
    private void startService(String keywordsString) {
        isRunning = true;
        totalCount = 0;
        greetingCount = 0;
        dailyLimitReached = false;
        clickedNodes.clear();
        
        // 将关键词字符串转换为列表，并去除空格
        keywords = new ArrayList<>();
        if (keywordsString != null && !keywordsString.isEmpty()) {
            String[] keywordArray = keywordsString.split(",");
            for (String keyword : keywordArray) {
                String trimmed = keyword.trim();
                if (!trimmed.isEmpty()) {
                    keywords.add(trimmed);
                }
            }
        }
        
        // 输出日志显示当前使用的关键词
        StringBuilder keywordsLog = new StringBuilder("当前使用的关键词: ");
        for (int i = 0; i < keywords.size(); i++) {
            keywordsLog.append(keywords.get(i));
            if (i < keywords.size() - 1) {
                keywordsLog.append(", ");
            }
        }
        logMessage(keywordsLog.toString());
        
        // 发送广播通知MainActivity服务已启动
        Intent broadcastIntent = new Intent(MainActivity.ACTION_SERVICE_STATUS_CHANGED);
        broadcastIntent.putExtra("running", true);
        broadcastIntent.putExtra("count", totalCount);
        sendBroadcast(broadcastIntent);
        
        logMessage("服务已启动");
        
        // 启动应用状态检查
        startAppStatusCheck();
    }

    // 修改停止服务的方法，确保完全停止所有任务
    private void stopService() {
        if (isServiceStopping) {
            logMessage("服务正在停止中，跳过重复停止");
            return;
        }
        
        // 立即设置停止标志
        isServiceStopping = true;
        isRunning = false;
        
        // 重置所有状态
        resetServiceState();

        logMessage("正在停止自动投递服务，共投递 " + totalCount + " 个岗位，打招呼 " + greetingCount + " 次");
        
        // 停止应用状态检查
        stopAppStatusCheck();
        
        // 发送广播通知 MainActivity 更新 UI
        Intent intent = new Intent(MainActivity.ACTION_SERVICE_STATUS_CHANGED);
        intent.putExtra("running", false);
        intent.putExtra("count", totalCount);
        sendBroadcast(intent);
        
        // 清理所有延迟任务
        handler.removeCallbacksAndMessages(null);
        mainHandler.removeCallbacksAndMessages(null);
        appStatusCheckHandler.removeCallbacksAndMessages(null);
        serviceWatchdogHandler.removeCallbacksAndMessages(null);
        
        // 最终确保所有操作都停止
        handler.postDelayed(() -> {
            logMessage("所有任务已彻底清理，服务已完全停止");
            isServiceStopping = false;  // 重置停止标志，但保持 isRunning 为 false
        }, 1000);
    }

    // 添加重置服务状态的方法
    private void resetServiceState() {
        // 重置所有计数器和状态标志
        totalCount = 0;
        greetingCount = 0;
        dailyLimitReached = false;
        clickedNodes.clear();
        currentState = State.BROWSING_LIST;
        sessionStartTime = 0;
        totalBackOperations = 0;
        consecutiveBackCount = 0;
        lastSuccessfulOperation = 0;
        lastUserInteractionTime = 0;
    }

    private void logMessage(String message) {
        Log.d(TAG, message);
        handler.post(() -> {
            try {
                MainActivity.appendLog(getApplicationContext(), message);
            } catch (Exception e) {
                Log.e(TAG, "Error updating log: " + e.getMessage());
            }
        });
    }

    @Override
    public void onInterrupt() {
        logMessage("服务被中断，尝试恢复");
        // 不立即将isRunning设置为false，给恢复机会
        
        // 延迟检查是否需要恢复服务
        handler.postDelayed(() -> {
            if (greetingCount < MAX_GREETING_COUNT && !isServiceStopping) {
                logMessage("服务中断后尝试恢复运行");
                isRunning = true;
                
                // 尝试恢复操作
                try {
                    AccessibilityNodeInfo rootNode = getRootInActiveWindow();
                    if (rootNode != null) {
                        PageType currentPage = getCurrentPageType(rootNode);
                        if (currentPage != null) {
                            handlePageByType(currentPage, rootNode);
                        } else {
                            // 无法识别页面类型，只记录日志
                            logMessage("服务恢复后无法识别当前页面类型");
                        }
                    } else {
                        // 无法获取窗口信息，只记录日志
                        logMessage("服务恢复后无法获取当前窗口信息");
                    }
                } catch (Exception e) {
                    logMessage("恢复服务时发生异常: " + e.getMessage());
                    // 如果失败，记录错误
                }
            } else {
                logMessage("服务被中断，且已达到最大打招呼次数或服务正在停止中，不再恢复");
        isRunning = false;
            }
        }, 5000);
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        logMessage("无障碍服务已连接");
        
        // 获取屏幕尺寸
        screenWidth = getResources().getDisplayMetrics().widthPixels;
        screenHeight = getResources().getDisplayMetrics().heightPixels;
        
        // 启动定时检查机制
        startPeriodicCheck();
        
        // 添加服务看门狗，确保服务持续运行
        startServiceWatchdog();
    }
    
    // 移除服务监视器启动方法中的重启逻辑
    private void startServiceWatchdog() {
        logMessage("启动服务监视器");
        
        if (serviceWatchdogRunnable != null) {
            serviceWatchdogHandler.removeCallbacks(serviceWatchdogRunnable);
        }
        
        serviceWatchdogRunnable = new Runnable() {
            @Override
            public void run() {
                // 检查服务是否应该运行但实际未运行
                if (!isServiceStopping && !isRunning) {
                    logMessage("服务已停止运行");
                }
                
                // 定期检查
                if (isRunning && !isServiceStopping) {
                    // 检查是否在BOSS直聘应用内，但不自动重启应用
                    checkIfBossAppRunning();
                }
                
                // 安排下次检查
                if (isRunning && !isServiceStopping) {
                    serviceWatchdogHandler.postDelayed(this, SERVICE_WATCHDOG_INTERVAL);
                }
            }
        };
        
        // 立即开始第一次检查
        serviceWatchdogHandler.post(serviceWatchdogRunnable);
    }

    private void clickCommunicateButton() {
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode == null) return;
        
        // 获取当前页面类型
        PageType currentPage = getCurrentPageType(rootNode);
        if (currentPage != PageType.JOB_DETAIL) {
            logMessage("当前不在职位详情页，取消点击沟通按钮");
            return;
        }
        
        // 使用新方法查找并点击底部沟通按钮
        findAndClickBottomButton(rootNode);
    }



    // 修改safeBackOperation方法，在主界面严格限制返回操作
    private void safeBackOperation() {
        // 检查是否在BOSS直聘应用内
        if (!isInBossApp()) {
            logMessage("警告：检测到已不在BOSS直聘应用内，取消返回操作");
            return;
        }
        
        // 根据当前页面类型检查返回次数限制
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode != null) {
            PageType currentPage = getCurrentPageType(rootNode);
            if (currentPage != null) {
                int maxAllowedBacks = MAX_CONSECUTIVE_BACKS; // 默认值
                
                // 根据页面类型设置最大允许返回次数
                switch (currentPage) {
                    case MAIN_LIST:
                        // 在主界面严格禁止返回操作
                        logMessage("当前在职位主界面，禁止执行返回操作");
                        // 检查是否有"再按一次退出程序"提示
                        List<AccessibilityNodeInfo> exitPromptNodes = rootNode.findAccessibilityNodeInfosByText("再按一次退出");
                        if (!exitPromptNodes.isEmpty()) {
                            logMessage("检测到退出提示，已在职位主界面，禁止执行返回操作，直接开始查找职位");
                            handler.postDelayed(() -> findAndClickJobs(getRootInActiveWindow()), MAIN_PAGE_LOAD_DELAY);
                        } else {
                            // 如果没有退出提示，继续查找职位
                            findAndClickJobs(rootNode);
                        }
                        return; // 直接返回，不执行后续返回操作
                    case JOB_DETAIL:
                        maxAllowedBacks = MAX_BACKS_DETAIL_PAGE;
                        break;
                    case CHAT_PAGE:
                        maxAllowedBacks = MAX_BACKS_CHAT_PAGE;
                        break;
                }
                
                // 检查是否超过当前页面允许的最大返回次数
                if (consecutiveBackCount >= maxAllowedBacks) {
                    logMessage("警告：当前页面(" + currentPage + ")返回次数已达上限(" + maxAllowedBacks + ")，取消返回操作");
                    return;
                }
            }
        }
        
        // 检查连续返回次数
        if (consecutiveBackCount >= MAX_CONSECUTIVE_BACKS) {
            logMessage("警告：检测到连续返回次数过多，暂停返回操作");
            // 强制等待一段时间后再允许返回
            handler.postDelayed(() -> {
                // 重置连续返回计数
                consecutiveBackCount = 0;
                logMessage("重置连续返回计数，现在可以继续操作");
            }, 5000); // 等待5秒
            return;
        }
        
        // 检查距离上次成功操作的时间
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastSuccessfulOperation > 30000) { // 如果30秒内没有成功操作
            logMessage("警告：长时间没有成功操作，可能卡在某个界面，停止服务");
            stopService();
            return;
        }
        
        // 检查总返回操作次数
        if (totalBackOperations >= MAX_BACK_OPERATIONS) {
            long sessionDuration = System.currentTimeMillis() - sessionStartTime;
            if (sessionDuration < 60000) { // 如果在1分钟内执行了过多返回操作
                logMessage("警告：短时间内返回操作过多，暂停服务");
                stopService();
                return;
            } else {
                // 重置计数器和会话时间
                totalBackOperations = 0;
                sessionStartTime = System.currentTimeMillis();
            }
        }
        
        // 检查短时间内返回次数
        if (currentTime - lastBackTime < 1000) { // 1秒内
            backOperationCount++;
            if (backOperationCount > 1) { // 1秒内超过1次返回
                logMessage("警告：短时间内返回操作过多，暂停2秒");
                backOperationCount = 0; // 重置计数器
                handler.postDelayed(this::safeBackOperation, 2000); // 延迟2秒后再尝试
                return;
            }
        } else {
            // 重置计数器
            backOperationCount = 0;
        }
        
        // 更新最后返回时间
        lastBackTime = currentTime;
        totalBackOperations++; // 增加总返回操作计数
        consecutiveBackCount++; // 增加连续返回计数
        
        // 执行返回操作
        performGlobalAction(GLOBAL_ACTION_BACK);
        logMessage("执行返回操作 (总计: " + totalBackOperations + ", 连续: " + consecutiveBackCount + ")");
    }

    // 修改isInBossApp方法，增强检测能力
    private boolean isInBossApp() {
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode == null) return false;
        
        CharSequence packageName = rootNode.getPackageName();
        boolean inBossApp = packageName != null && packageName.toString().contains("com.hpbr.bosszhipin");
        
        if (!inBossApp) {
            logMessage("检测到已离开BOSS直聘应用");
        }
        
        return inBossApp;
    }

    // 添加一个方法来重置连续返回计数
    private void resetConsecutiveBackCount() {
        consecutiveBackCount = 0;
        lastSuccessfulOperation = System.currentTimeMillis();
        logMessage("重置连续返回计数");
    }

    // 为节点生成唯一标识符
    private String generateNodeId(AccessibilityNodeInfo node) {
        if (node == null) return "";
        
        // 获取节点的边界信息
        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        
        // 获取节点的文本内容
        String text = "";
        if (node.getText() != null) {
            text = node.getText().toString();
        }
        
        // 获取节点的描述内容
        String description = "";
        if (node.getContentDescription() != null) {
            description = node.getContentDescription().toString();
        }
        
        // 组合信息生成唯一标识符
        return text + "_" + description + "_" + bounds.toString();
    }

    // 修改performSingleBackAndCheck方法，增加页面类型检查
    private void performSingleBackAndCheck(Runnable onMainPageFound, Runnable onOtherPageFound) {
        // 先检查当前页面类型
        AccessibilityNodeInfo currentRootNode = getRootInActiveWindow();
        if (currentRootNode != null) {
            PageType currentPage = getCurrentPageType(currentRootNode);
            
            // 如果当前已经在主界面，不执行返回操作
            if (currentPage == PageType.MAIN_LIST) {
                logMessage("当前已在主界面，无需返回");
                currentState = State.BROWSING_LIST;
                resetConsecutiveBackCount();
                
                // 执行主界面回调
                if (onMainPageFound != null) {
                    onMainPageFound.run();
                }
                return;
            }
        }
        
        // 先执行一次返回
        safeBackOperation();
        
        // 等待页面加载
        handler.postDelayed(() -> {
            // 检查当前页面
            AccessibilityNodeInfo rootNode = getRootInActiveWindow();
            if (rootNode == null) {
                logMessage("警告：返回后无法获取页面信息，可能已退出应用");
                stopService();
                return;
            }
            
            PageType currentPage = getCurrentPageType(rootNode);
            logMessage("返回后检测到页面类型: " + (currentPage != null ? currentPage.toString() : "未知"));
            
            // 如果已经回到主界面，不再执行额外的返回操作
            if (currentPage == PageType.MAIN_LIST) {
                logMessage("已返回到主界面，不再执行额外返回");
                currentState = State.BROWSING_LIST;
                resetConsecutiveBackCount();
                
                // 执行主界面回调
                if (onMainPageFound != null) {
                    onMainPageFound.run();
                }
            } else {
                // 执行其他页面回调
                if (onOtherPageFound != null) {
                    onOtherPageFound.run();
                }
            }
        }, 1500); // 等待1.5秒检查页面
    }

    // 修改检查是否已发送打招呼消息的方法
    private boolean checkIfGreetingSent(AccessibilityNodeInfo rootNode) {
        if (rootNode == null) return false;
        
        logMessage("开始检查是否已发送打招呼消息");
        
        // 检查是否有包含打招呼内容的消息
        List<AccessibilityNodeInfo> messageNodes = findNodesByClassName(rootNode, "android.widget.TextView");
        logMessage("找到 " + messageNodes.size() + " 个文本节点");
        
        for (AccessibilityNodeInfo node : messageNodes) {
            if (node.getText() != null) {
                String messageText = node.getText().toString();
                // 记录所有消息文本，帮助调试
                if (messageText.length() > 0) {
                    logMessage("消息文本: " + messageText);
                }
                
                // 检查消息是否包含打招呼内容
                if (messageText.contains(GREETING_MESSAGE)) {
                    logMessage("检测到已发送的打招呼消息");
                    return true;
                }
                
                // 检查是否包含其他可能的打招呼内容
                if (messageText.contains("我是") || messageText.contains("桂晨") || 
                    messageText.contains("您好") || messageText.contains("你好")) {
                    logMessage("检测到可能的打招呼消息: " + messageText);
                    return true;
                }
            }
        }
        
        // 尝试通过其他方式查找打招呼消息
        List<AccessibilityNodeInfo> allNodes = getAllNodesFromRoot(rootNode);
        for (AccessibilityNodeInfo node : allNodes) {
            if (node.getText() != null) {
                String text = node.getText().toString();
                if (text.contains(GREETING_MESSAGE) || text.contains("我是") || 
                    text.contains("桂晨") || text.contains("您好") || text.contains("你好")) {
                    logMessage("通过全节点搜索检测到可能的打招呼消息: " + text);
                    return true;
                }
            }
        }
        
        logMessage("未检测到已发送的打招呼消息");
        return false;
    }
    
    // 修改performDoubleBackToMainPage方法，修复变量重复定义错误
    private void performDoubleBackToMainPage() {
        logMessage("执行双重返回到主界面操作");
        
        // 先检查当前是否已经在职位主界面
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode != null) {
            // 首先检查是否有"再按一次退出程序"提示
            List<AccessibilityNodeInfo> exitPromptNodes = rootNode.findAccessibilityNodeInfosByText("再按一次退出");
            if (!exitPromptNodes.isEmpty()) {
                logMessage("检测到退出提示，已在职位主界面，不执行返回操作，直接查找职位");
                handler.postDelayed(() -> {
                    AccessibilityNodeInfo finalRootNode = getRootInActiveWindow();
                    if (finalRootNode != null) {
                        findAndClickJobs(finalRootNode);
                    }
                }, MAIN_PAGE_LOAD_DELAY);
                return;
            }
            
            // 检查是否有职位标签且被选中
            List<AccessibilityNodeInfo> tabNodes = rootNode.findAccessibilityNodeInfosByText("职位");
            for (AccessibilityNodeInfo node : tabNodes) {
                if (node.isSelected()) {
                    logMessage("检测到当前已在职位主界面(职位标签已选中)，不执行返回操作，直接查找职位");
                    handler.postDelayed(() -> {
                        AccessibilityNodeInfo finalRootNode = getRootInActiveWindow();
                        if (finalRootNode != null) {
                            findAndClickJobs(finalRootNode);
                        }
                    }, MAIN_PAGE_LOAD_DELAY);
                    return;
                }
            }
        }
        
        // 直接执行两次返回操作，不再检查页面类型
        logMessage("执行第一次返回操作");
        performGlobalAction(GLOBAL_ACTION_BACK);
        
        // 延迟后执行第二次返回
        handler.postDelayed(() -> {
            // 再次检查是否已经回到职位主界面
            AccessibilityNodeInfo checkNode = getRootInActiveWindow();
            if (checkNode != null) {
                // 首先检查是否有"再按一次退出程序"提示
                List<AccessibilityNodeInfo> exitPromptNodes = checkNode.findAccessibilityNodeInfosByText("再按一次退出");
                if (!exitPromptNodes.isEmpty()) {
                    logMessage("第一次返回后检测到退出提示，已在职位主界面，不执行第二次返回，直接查找职位");
                    handler.postDelayed(() -> {
                        AccessibilityNodeInfo finalRootNode = getRootInActiveWindow();
                        if (finalRootNode != null) {
                            findAndClickJobs(finalRootNode);
                        }
                    }, MAIN_PAGE_LOAD_DELAY);
                    return;
                }
                
                // 检查是否有职位标签且被选中
                List<AccessibilityNodeInfo> tabNodes = checkNode.findAccessibilityNodeInfosByText("职位");
                for (AccessibilityNodeInfo node : tabNodes) {
                    if (node.isSelected()) {
                        logMessage("第一次返回后已在职位主界面(职位标签已选中)，不执行第二次返回，直接查找职位");
                        handler.postDelayed(() -> {
                            AccessibilityNodeInfo finalRootNode = getRootInActiveWindow();
                            if (finalRootNode != null) {
                                findAndClickJobs(finalRootNode);
                            }
                        }, MAIN_PAGE_LOAD_DELAY);
                        return;
                    }
                }
            }
            
            logMessage("执行第二次返回操作");
            performGlobalAction(GLOBAL_ACTION_BACK);
            
            // 延迟后开始查找职位
            handler.postDelayed(() -> {
                logMessage("返回操作完成，开始查找职位");
                AccessibilityNodeInfo finalRootNode = getRootInActiveWindow();
                if (finalRootNode != null) {
                    findAndClickJobs(finalRootNode);
                }
            }, BACK_OPERATION_DELAY);
        }, BACK_OPERATION_DELAY);
    }

    // 添加聊天页面返回操作方法
    private void performChatPageBackOperation() {
        logMessage("执行聊天页面返回操作");
        
        // 先执行一次返回
        performGlobalAction(GLOBAL_ACTION_BACK);
        
        // 延迟后再执行一次返回
        handler.postDelayed(() -> {
            performGlobalAction(GLOBAL_ACTION_BACK);
            
            // 延迟后检查是否回到主界面
            handler.postDelayed(() -> {
                AccessibilityNodeInfo checkNode = getRootInActiveWindow();
                if (checkNode != null) {
                    PageType checkPage = getCurrentPageType(checkNode);
                    if (checkPage == PageType.MAIN_LIST) {
                        logMessage("成功回到主界面，开始查找职位");
                        // 增加打招呼计数
                        greetingCount++;
                        logMessage("当前已打招呼次数: " + greetingCount + "/" + MAX_GREETING_COUNT);
                        
                        // 检查是否达到最大打招呼次数
                        if (greetingCount >= MAX_GREETING_COUNT) {
                            logMessage("已达到最大打招呼次数 " + MAX_GREETING_COUNT + "，准备停止服务");
                            handler.postDelayed(() -> {
                                stopService();
                            }, 3000);
                            return;
                        }
                        
                        handler.postDelayed(() -> findAndClickJobs(getRootInActiveWindow()), MAIN_PAGE_LOAD_DELAY);
                    } else {
                        logMessage("两次返回后未回到主界面，尝试强制返回");
                        forceReturnToMainPage();
                    }
                }
            }, BACK_OPERATION_DELAY);
        }, BACK_OPERATION_DELAY);
    }
    
    // 修改forceReturnToMainPage方法，避免多次点击
    private void forceReturnToMainPage() {
        logMessage("执行强制返回到主界面操作");
        
        // 检查当前页面类型
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode != null) {
            PageType currentPage = getCurrentPageType(rootNode);
            
            // 如果已经在主界面，直接开始查找职位
            if (currentPage == PageType.MAIN_LIST) {
                logMessage("当前已在主界面，直接开始查找职位");
                handler.postDelayed(() -> findAndClickJobs(getRootInActiveWindow()), MAIN_PAGE_LOAD_DELAY);
                return;
            }
        }
        
        // 尝试点击底部"职位"标签
        if (rootNode != null) {
            clickPositionTab(rootNode);
            return;
        }
        
        // 如果找不到底部标签，尝试多次返回
        logMessage("找不到底部标签，尝试多次返回");
        
        // 使用更温和的返回策略，每次返回之间增加延迟
        for (int i = 0; i < 3; i++) {
            final int index = i;
            handler.postDelayed(() -> {
                performGlobalAction(GLOBAL_ACTION_BACK);
            }, 1000 * index); // 每次返回之间间隔1秒
        }
        
        // 延迟后检查
        handler.postDelayed(() -> {
            AccessibilityNodeInfo finalNode = getRootInActiveWindow();
            if (finalNode != null) {
                PageType finalPage = getCurrentPageType(finalNode);
                if (finalPage == PageType.MAIN_LIST) {
                    logMessage("强制返回成功，已回到主界面");
                    handler.postDelayed(() -> findAndClickJobs(getRootInActiveWindow()), MAIN_PAGE_LOAD_DELAY);
                } else {
                    logMessage("强制返回失败，尝试重启APP");
                    // 最后尝试点击"职位"标签
                    clickPositionTab(finalNode);
                }
            }
        }, BACK_OPERATION_DELAY + 3000); // 增加延迟时间，确保所有返回操作都已完成
    }
    
    // 添加点击底部"职位"标签的方法
    private void clickPositionTab(AccessibilityNodeInfo rootNode) {
        if (rootNode == null) return;
        
        // 检查是否已经点击过职位标签
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastPositionTabClickTime < MIN_TAB_CLICK_INTERVAL) {
            logMessage("职位标签点击过于频繁，延迟执行");
                return;
            }
            
        lastPositionTabClickTime = currentTime;
        
        // 尝试查找职位标签
        List<AccessibilityNodeInfo> positionTabs = rootNode.findAccessibilityNodeInfosByText("职位");
        if (!positionTabs.isEmpty()) {
            for (AccessibilityNodeInfo tab : positionTabs) {
                if (tab.isClickable()) {
                    logMessage("找到职位标签并点击");
                    clickNode(tab);
                    
                    // 延迟检查点击效果
                    handler.postDelayed(() -> {
                        AccessibilityNodeInfo checkNode = getRootInActiveWindow();
                        if (checkNode != null) {
                            PageType checkPage = getCurrentPageType(checkNode);
                            if (checkPage == PageType.MAIN_LIST) {
                                logMessage("点击职位标签成功，已回到主界面");
                                handler.postDelayed(() -> findAndClickJobs(getRootInActiveWindow()), MAIN_PAGE_LOAD_DELAY);
                            } else {
                                logMessage("点击'职位'标签后未回到主界面，再次尝试点击");
                                // 不立即重启，而是延迟一段时间后再检查
                                handler.postDelayed(() -> {
                                    AccessibilityNodeInfo finalCheckNode = getRootInActiveWindow();
                                    if (finalCheckNode != null) {
                                        PageType finalCheckPage = getCurrentPageType(finalCheckNode);
                                        if (finalCheckPage == PageType.MAIN_LIST) {
                                            logMessage("延迟检查发现已回到主界面，开始查找职位");
                                            handler.postDelayed(() -> findAndClickJobs(getRootInActiveWindow()), MAIN_PAGE_LOAD_DELAY);
                                        } else {
                                            logMessage("多次检查后仍未回到主界面，执行多次返回操作");
                                            forceNavigateBack();
                                        }
                                    }
                                }, 2000); // 延迟2秒再次检查
                            }
                        }
                    }, 2000);
                    
                    return;
                }
            }
        }
        
        // 如果找不到职位标签，尝试通过底部导航栏查找
        List<AccessibilityNodeInfo> tabBarNodes = rootNode.findAccessibilityNodeInfosByViewId("com.hpbr.bosszhipin:id/tab_bar");
        if (!tabBarNodes.isEmpty()) {
            AccessibilityNodeInfo tabBar = tabBarNodes.get(0);
            List<AccessibilityNodeInfo> children = new ArrayList<>();
            collectChildNodes(tabBar, children);
            
            for (AccessibilityNodeInfo child : children) {
                if (child.getText() != null && child.getText().toString().contains("职位")) {
                    logMessage("通过导航栏找到职位标签并点击");
                    clickNode(child);
                    return;
                }
            }
        }
        
        logMessage("未找到职位标签，尝试通过屏幕位置点击");
        // 尝试通过屏幕位置点击（职位标签通常在底部导航栏中间偏左位置）
        int x = screenWidth / 5; // 屏幕宽度的1/5位置
        int y = screenHeight - 50; // 屏幕底部上方50像素
        
        clickAtPosition(x, y);
    }
    
    // 添加查找包含指定文本列表的节点的方法
    private void findNodesWithTexts(AccessibilityNodeInfo root, List<String> texts, List<AccessibilityNodeInfo> result) {
        if (root == null) return;
        
        // 检查当前节点
        if (root.getText() != null) {
            String nodeText = root.getText().toString();
            for (String text : texts) {
                if (nodeText.contains(text)) {
                    result.add(root);
                    break;
                }
            }
        }
        
        // 递归检查所有子节点
        for (int i = 0; i < root.getChildCount(); i++) {
            AccessibilityNodeInfo child = root.getChild(i);
            if (child != null) {
                findNodesWithTexts(child, texts, result);
            }
        }
    }

    // 修改handleIntelligentReturn方法，增加延迟检测逻辑
    private void handleIntelligentReturn(AccessibilityNodeInfo rootNode) {
        if (rootNode == null) return;
        
        PageType currentPage = getCurrentPageType(rootNode);
        logMessage("智能返回: 当前界面类型为 " + (currentPage != null ? currentPage.toString() : "未知"));
        
        // 如果无法确定界面类型，延迟3秒后再次检测
        if (currentPage == null) {
            logMessage("无法确定界面类型，延迟3秒后再次检测");
            handler.postDelayed(() -> {
                AccessibilityNodeInfo delayedRootNode = getRootInActiveWindow();
                if (delayedRootNode != null) {
                    PageType delayedPage = getCurrentPageType(delayedRootNode);
                    logMessage("延迟检测后界面类型为: " + (delayedPage != null ? delayedPage.toString() : "仍未知"));
                    
                    if (delayedPage != null) {
                        // 如果延迟检测后确定了界面类型，根据类型执行相应操作
                        handleIntelligentReturnByType(delayedPage, delayedRootNode);
                    } else {
                        // 如果仍无法确定界面类型，执行通用返回操作
                        logMessage("延迟检测后仍无法确定界面类型，执行通用返回操作");
                        performSimpleReturn(2); // 执行两次返回操作
                    }
                }
            }, 3000);
            return;
        }
        
        // 如果已确定界面类型，根据类型执行相应操作
        handleIntelligentReturnByType(currentPage, rootNode);
    }
    
    // 添加根据界面类型执行智能返回的方法
    private void handleIntelligentReturnByType(PageType pageType, AccessibilityNodeInfo rootNode) {
        if (pageType == PageType.JOB_DETAIL) {
            // 如果在职位详情页(b)，执行一次返回操作
            logMessage("检测到在职位详情页，执行一次返回操作");
            performGlobalAction(GLOBAL_ACTION_BACK);
            
            // 延迟1500毫秒后检查是否回到主界面
            handler.postDelayed(() -> {
                AccessibilityNodeInfo newRootNode = getRootInActiveWindow();
                if (newRootNode != null) {
                    PageType newPage = getCurrentPageType(newRootNode);
                    if (newPage == PageType.MAIN_LIST) {
                        logMessage("返回成功，已回到主界面");
                        handler.postDelayed(() -> findAndClickJobs(getRootInActiveWindow()), MAIN_PAGE_LOAD_DELAY);
                    } else {
                        logMessage("返回后仍未回到主界面，尝试强制返回到主界面");
                        forceReturnToMainPage();
                    }
                }
            }, 1500);
        } else if (pageType == PageType.CHAT_PAGE) {
            // 如果在聊天页面(c)，执行两次返回操作
            logMessage("检测到在聊天页面，执行两次返回操作");
            performGlobalAction(GLOBAL_ACTION_BACK);
            
            // 延迟1000毫秒后执行第二次返回
            handler.postDelayed(() -> {
                performGlobalAction(GLOBAL_ACTION_BACK);
                
                // 延迟1500毫秒后检查是否回到主界面
                handler.postDelayed(() -> {
                    AccessibilityNodeInfo newRootNode = getRootInActiveWindow();
                    if (newRootNode != null) {
                        PageType newPage = getCurrentPageType(newRootNode);
                        if (newPage == PageType.MAIN_LIST) {
                            logMessage("两次返回成功，已回到主界面");
                            handler.postDelayed(() -> findAndClickJobs(getRootInActiveWindow()), MAIN_PAGE_LOAD_DELAY);
                        } else {
                            logMessage("两次返回后仍未回到主界面，尝试强制返回到主界面");
                            forceReturnToMainPage();
                        }
                    }
                }, 1500);
            }, 1000);
        } else if (pageType == PageType.MAIN_LIST) {
            // 如果已经在主界面，直接查找职位
            logMessage("检测到已在主界面，直接查找职位");
            handler.postDelayed(() -> findAndClickJobs(getRootInActiveWindow()), MAIN_PAGE_LOAD_DELAY);
        } else {
            // 如果是其他未知界面，尝试强制返回到主界面
            logMessage("当前在未知界面，尝试强制返回到主界面");
            forceReturnToMainPage();
        }
    }
    
    // 完整替换整个方法
    private void executeReturnOperation(int times) {
        // 检查距离上次返回操作的时间间隔
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastBackOperationTime < MIN_BACK_INTERVAL) {
            logMessage("距离上次返回操作时间不足" + (MIN_BACK_INTERVAL/1000) + "秒，延迟执行");
            handler.postDelayed(() -> executeReturnOperation(times), MIN_BACK_INTERVAL - (currentTime - lastBackOperationTime) + 1000);
            return;
        }
        
        // 先检查当前是否有"再按一次退出程序"提示
        AccessibilityNodeInfo currentRootNode = getRootInActiveWindow();
        if (currentRootNode != null) {
            List<AccessibilityNodeInfo> exitPromptNodes = currentRootNode.findAccessibilityNodeInfosByText("再按一次退出");
            if (!exitPromptNodes.isEmpty()) {
                logMessage("检测到退出提示，已在职位主界面，禁止执行返回操作，直接开始查找职位");
                handler.postDelayed(() -> findAndClickJobs(getRootInActiveWindow()), MAIN_PAGE_LOAD_DELAY);
                return;
            }
            
            // 检查当前包名，判断是否已退出BOSS直聘
            if (currentRootNode.getPackageName() != null && 
                !currentRootNode.getPackageName().toString().equals(BOSS_PACKAGE_NAME)) {
                logMessage("检测到已退出BOSS直聘，尝试重启APP");
                    handler.postDelayed(() -> restartBossApp(), APP_OPERATION_DELAY);
                return;
            }
        }
        
        // 在返回前记录当前界面类型
        AccessibilityNodeInfo rootNodeBeforeBack = getRootInActiveWindow();
        if (rootNodeBeforeBack != null) {
            previousPageType = getCurrentPageType(rootNodeBeforeBack);
            logMessage("返回前界面类型: " + (previousPageType != null ? previousPageType.toString() : "未知"));
        }
        
        // 更新最后返回操作时间
        lastBackOperationTime = System.currentTimeMillis();
        
        // 执行第一次返回
        performGlobalAction(GLOBAL_ACTION_BACK);
        logMessage("执行第一次返回操作");
        
        // 如果需要多次返回
        if (times > 1) {
            // 延迟后执行第二次返回
        handler.postDelayed(() -> {
                // 检查是否已经回到主界面
                AccessibilityNodeInfo rootNodeAfterFirstBack = getRootInActiveWindow();
                if (rootNodeAfterFirstBack != null) {
                // 检查是否有"再按一次退出程序"提示
                    List<AccessibilityNodeInfo> exitPromptNodes = rootNodeAfterFirstBack.findAccessibilityNodeInfosByText("再按一次退出");
                if (!exitPromptNodes.isEmpty()) {
                        logMessage("第一次返回后检测到退出提示，已在职位主界面，禁止继续返回，直接开始查找职位");
                    handler.postDelayed(() -> findAndClickJobs(getRootInActiveWindow()), MAIN_PAGE_LOAD_DELAY);
                    return;
                }
                
                    // 执行第二次返回
                            lastBackOperationTime = System.currentTimeMillis();
                            performGlobalAction(GLOBAL_ACTION_BACK);
                            logMessage("执行第二次返回操作");
                            
                    // 延迟后检查是否回到主界面
                            handler.postDelayed(() -> {
                        AccessibilityNodeInfo rootNodeAfterSecondBack = getRootInActiveWindow();
                        if (rootNodeAfterSecondBack != null) {
                                    // 检查是否有"再按一次退出程序"提示
                            List<AccessibilityNodeInfo> secondExitPromptNodes = rootNodeAfterSecondBack.findAccessibilityNodeInfosByText("再按一次退出");
                            if (!secondExitPromptNodes.isEmpty()) {
                                        logMessage("第二次返回后检测到退出提示，已在职位主界面，直接开始查找职位");
                                        handler.postDelayed(() -> findAndClickJobs(getRootInActiveWindow()), MAIN_PAGE_LOAD_DELAY);
                                        return;
                                    }
                                    }
                    }, 1500);
                                }
            }, BACK_OPERATION_DELAY);
                    } else {
            // 如果只需要返回一次，延迟后检查结果
                    handler.postDelayed(() -> {
                AccessibilityNodeInfo rootNodeAfterBack = getRootInActiveWindow();
                if (rootNodeAfterBack != null) {
                                                    // 检查是否有"再按一次退出程序"提示
                    List<AccessibilityNodeInfo> exitPromptNodes = rootNodeAfterBack.findAccessibilityNodeInfosByText("再按一次退出");
                    if (!exitPromptNodes.isEmpty()) {
                        logMessage("返回后检测到退出提示，已在职位主界面，直接开始查找职位");
                                                        handler.postDelayed(() -> findAndClickJobs(getRootInActiveWindow()), MAIN_PAGE_LOAD_DELAY);
                    }
                }
            }, 1500);
    }
    }

    // 修改sendGreetingMessage方法，添加计数功能
    private void sendGreetingMessage() {
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode == null) return;
        
        // 检查是否已经发送过打招呼消息
        if (greetingDetected || greetingSent) {
            logMessage("已发送过打招呼消息，跳过");
            // 已发送过消息，直接返回主界面
            performDoubleBackToMainPage();
            return;
        }
        
        // 等待系统自动发送打招呼消息
        logMessage("等待系统自动发送打招呼消息");
        
        // 延迟3秒后检查是否已发送打招呼消息
        handler.postDelayed(() -> {
            AccessibilityNodeInfo newRootNode = getRootInActiveWindow();
            if (newRootNode != null) {
                boolean hasGreeting = checkIfGreetingSent(newRootNode);
                if (hasGreeting) {
                    logMessage("检测到系统已自动发送打招呼消息，准备返回主界面");
                    performDoubleBackToMainPage();
                } else {
                    logMessage("等待3秒后仍未检测到打招呼消息，继续等待");
                    // 再延迟3秒检查
                    handler.postDelayed(() -> {
                        AccessibilityNodeInfo finalRootNode = getRootInActiveWindow();
                        if (finalRootNode != null) {
                            boolean finalHasGreeting = checkIfGreetingSent(finalRootNode);
                            if (finalHasGreeting) {
                                logMessage("最终检测到系统已自动发送打招呼消息，准备返回主界面");
                                performDoubleBackToMainPage();
                            } else {
                                logMessage("多次等待后仍未检测到打招呼消息，尝试返回主界面");
                                performDoubleBackToMainPage();
                            }
                        }
                    }, 3000);
                }
            }
        }, 3000);
    }

    // 添加一个方法查找具有精确文本的节点
    private void findNodesWithExactText(AccessibilityNodeInfo root, String text, List<AccessibilityNodeInfo> result) {
        if (root == null) return;
        
        // 检查当前节点
        if (root.getText() != null && text.equals(root.getText().toString())) {
            result.add(root);
        }
        
        // 递归检查所有子节点
        for (int i = 0; i < root.getChildCount(); i++) {
            AccessibilityNodeInfo child = root.getChild(i);
            if (child != null) {
                findNodesWithExactText(child, text, result);
            }
        }
    }

    // 添加一个方法查找节点的可点击父节点
    private AccessibilityNodeInfo findClickableParent(AccessibilityNodeInfo node) {
        if (node == null) return null;
        
        AccessibilityNodeInfo parent = node.getParent();
        while (parent != null) {
            if (parent.isClickable()) {
                return parent;
            }
            AccessibilityNodeInfo temp = parent;
            parent = parent.getParent();
            // 避免内存泄漏
            if (temp != node) {
                temp.recycle();
            }
        }
        
        return null;
    }
    
    // 添加一个方法获取所有节点
    private List<AccessibilityNodeInfo> getAllNodesFromRoot(AccessibilityNodeInfo root) {
        List<AccessibilityNodeInfo> nodes = new ArrayList<>();
        if (root == null) return nodes;
        
        // 使用广度优先搜索遍历所有节点
        List<AccessibilityNodeInfo> queue = new ArrayList<>();
        queue.add(root);
        
        while (!queue.isEmpty()) {
            AccessibilityNodeInfo node = queue.remove(0);
            nodes.add(node);
            
            // 添加所有子节点到队列
            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) {
                    queue.add(child);
                }
            }
        }
        
        return nodes;
    }

    // 添加缺失的performBackToMainPage方法
    private void performBackToMainPage() {
        logMessage("执行返回主界面操作");
        // 重置连续返回计数
        consecutiveBackCount = 0;
        
        // 执行返回操作并检查是否回到主界面
        performSingleBackAndCheck(
            // 如果返回到主界面
            () -> {
                handler.postDelayed(() -> findAndClickJobs(getRootInActiveWindow()), MAIN_PAGE_LOAD_DELAY);
            },
            // 如果返回后不在主界面，再次尝试返回
            () -> {
                logMessage("返回后仍不在主界面，再次尝试返回");
                consecutiveBackCount = 0;
                performSingleBackAndCheck(
                    // 如果第二次返回到主界面
                    () -> {
                        handler.postDelayed(() -> findAndClickJobs(getRootInActiveWindow()), MAIN_PAGE_LOAD_DELAY);
                    },
                    // 如果第二次返回后仍不在主界面
                    () -> {
                        logMessage("两次返回后仍不在主界面，暂停操作");
                        stopService();
                    }
                );
            }
        );
    }

    // 修改findAndClickBottomButton方法，点击沟通按钮后直接执行返回操作
    private void findAndClickBottomButton(AccessibilityNodeInfo rootNode) {
        if (rootNode == null) return;
        
        logMessage("尝试查找并点击底部沟通按钮");
        
        // 1. 首先尝试通过ID查找底部按钮
        List<AccessibilityNodeInfo> buttonNodes = rootNode.findAccessibilityNodeInfosByViewId("com.hpbr.bosszhipin:id/btn_chat");
        if (!buttonNodes.isEmpty()) {
            logMessage("通过ID找到底部按钮，准备点击");
            clickNode(buttonNodes.get(0));
            resetConsecutiveBackCount();
            currentState = State.COMMUNICATING;
            
            // 点击沟通按钮后，直接假设进入了聊天页面，延迟后执行返回操作
            handler.postDelayed(() -> {
                // 先检查是否有系统限制提示
                AccessibilityNodeInfo chatRootNode = getRootInActiveWindow();
                if (chatRootNode != null) {
                    // 检查是否有聊天页面特有的功能按钮
                    List<AccessibilityNodeInfo> sendResumeNodes = chatRootNode.findAccessibilityNodeInfosByText("发简历");
                    List<AccessibilityNodeInfo> changePhoneNodes = chatRootNode.findAccessibilityNodeInfosByText("换电话");
                    List<AccessibilityNodeInfo> changeWechatNodes = chatRootNode.findAccessibilityNodeInfosByText("换微信");
                    List<AccessibilityNodeInfo> notInterestedNodes = chatRootNode.findAccessibilityNodeInfosByText("不感兴趣");
                    
                    // 如果检测到聊天页面特征，立即处理聊天页面并返回
                    int featureCount = 0;
                    if (!sendResumeNodes.isEmpty()) featureCount++;
                    if (!changePhoneNodes.isEmpty()) featureCount++;
                    if (!changeWechatNodes.isEmpty()) featureCount++;
                    if (!notInterestedNodes.isEmpty()) featureCount++;
                    
                    if (featureCount >= 2) {
                        logMessage("在滑动前检测到聊天页面特征，立即处理聊天页面");
                        handleChatPageDetected();
                        return;
                    }
                }
                
                logMessage("点击沟通按钮后延迟执行，直接处理为聊天页面");
                
                // 只有未计数的职位才增加打招呼计数
                if (!currentJobId.isEmpty() && !hasCountedCurrentJob) {
                // 增加打招呼计数
                greetingCount++;
                    totalCount++; // 同时增加总处理职位数
                    hasCountedCurrentJob = true;
                    logMessage("【新】处理新职位，当前已打招呼次数: " + greetingCount + "/" + MAX_GREETING_COUNT + "，总处理职位: " + totalCount);
                
                // 检查是否达到最大打招呼次数
                    if (greetingCount >= MAX_GREETING_COUNT && !isServiceStopping) {
                        logMessage("【重要】已达到最大打招呼次数 " + MAX_GREETING_COUNT + "，准备停止服务");
                        isServiceStopping = true;
                    handler.postDelayed(() -> {
                        stopService();
                        }, 2000);
                    return;
                    }
                } else {
                    logMessage("【跳过】当前职位已计数或无效，不增加打招呼次数");
                }
                
                // 执行双重返回操作
                performDoubleBackToMainPage();
            }, 3000);
            return;
        }
        
        // 2. 尝试通过文本查找"立即沟通"按钮
        List<AccessibilityNodeInfo> immediateNodes = rootNode.findAccessibilityNodeInfosByText("立即沟通");
        if (!immediateNodes.isEmpty()) {
            logMessage("找到'立即沟通'按钮，准备点击");
            for (AccessibilityNodeInfo node : immediateNodes) {
                if (node.isClickable()) {
                    clickNode(node);
                    logMessage("点击了'立即沟通'按钮");
                    resetConsecutiveBackCount();
                    currentState = State.COMMUNICATING;
                    
                    // 点击沟通按钮后，直接假设进入了聊天页面，延迟后执行返回操作
                    handler.postDelayed(() -> {
                        logMessage("点击立即沟通按钮后延迟执行，直接处理为聊天页面");
                        // 增加打招呼计数
                        greetingCount++;
                        logMessage("当前已打招呼次数: " + greetingCount + "/" + MAX_GREETING_COUNT);
                        
                        // 不再检查是否达到最大打招呼次数
                        // 移除下面的检查代码
                        // if (greetingCount >= MAX_GREETING_COUNT && !isServiceStopping) {
                        //    ...
                        // }
                        
                        // 执行双重返回操作
                        performDoubleBackToMainPage();
                    }, 2000);
                    return;
                }
            }
        }
        
        // 3. 尝试通过文本查找"继续沟通"按钮
        List<AccessibilityNodeInfo> continueNodes = rootNode.findAccessibilityNodeInfosByText("继续沟通");
        if (!continueNodes.isEmpty()) {
            logMessage("找到'继续沟通'按钮，准备点击");
            for (AccessibilityNodeInfo node : continueNodes) {
                if (node.isClickable()) {
                    clickNode(node);
                    logMessage("点击了'继续沟通'按钮");
                    resetConsecutiveBackCount();
                    currentState = State.COMMUNICATING;
                    return;
                }
            }
        }
        
        // 4. 尝试查找底部的任何按钮
        List<AccessibilityNodeInfo> allButtons = findNodesByClassName(rootNode, "android.widget.Button");
        for (AccessibilityNodeInfo button : allButtons) {
            if (button.getText() != null) {
                String buttonText = button.getText().toString();
                if (buttonText.contains("沟通") || buttonText.contains("交谈") || buttonText.contains("聊一聊")) {
                    logMessage("找到底部按钮: " + buttonText);
                    clickNode(button);
                    resetConsecutiveBackCount();
                    currentState = State.COMMUNICATING;
                    return;
                }
            }
        }
        
        // 5. 尝试查找底部区域的可点击元素
        List<AccessibilityNodeInfo> bottomNodes = findNodesInBottomArea(rootNode);
        for (AccessibilityNodeInfo node : bottomNodes) {
            if (node.isClickable()) {
                logMessage("找到底部区域可点击元素，尝试点击");
                clickNode(node);
                resetConsecutiveBackCount();
                currentState = State.COMMUNICATING;
                return;
            }
        }
        
        // 6. 如果仍然找不到，尝试点击屏幕底部中央位置
        logMessage("未找到底部按钮，尝试点击屏幕底部中央位置");
        if (screenWidth > 0 && screenHeight > 0) {
            clickAtPosition(screenWidth / 2, (int)(screenHeight * 0.9));
            resetConsecutiveBackCount();
            currentState = State.COMMUNICATING;
            return;
        }
        
        // 7. 最后尝试通过坐标直接点击屏幕底部中央位置
        logMessage("尝试通过多点触控模拟点击底部按钮");
        
        // 先点击底部中央位置
        clickAtPosition(screenWidth / 2, (int)(screenHeight * 0.92));
        
        // 延迟100毫秒后，再点击稍微偏上一点的位置
        handler.postDelayed(() -> {
            clickAtPosition(screenWidth / 2, (int)(screenHeight * 0.88));
        }, 100);
        
        // 延迟200毫秒后，再点击稍微偏下一点的位置
        handler.postDelayed(() -> {
            clickAtPosition(screenWidth / 2, (int)(screenHeight * 0.95));
        }, 200);
        
        resetConsecutiveBackCount();
        currentState = State.COMMUNICATING;
    }

    // 在类中添加 handlePageByType 方法
    // 添加根据页面类型处理的方法
    private void handlePageByType(PageType pageType, AccessibilityNodeInfo rootNode) {
        if (rootNode == null) return;
        
        switch (pageType) {
            case MAIN_LIST:
            logMessage("检测到主界面，准备查找职位");
                handleMainList(rootNode);
                break;
                
            case JOB_DETAIL:
                // 如果是首次进入详情页，记录时间
                if (lastDetailPageTime == 0) {
                    lastDetailPageTime = System.currentTimeMillis();
                    logMessage("进入职位详情页面，开始计时");
                }
                
                // 检查是否在详情页停留超时
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastDetailPageTime > DETAIL_PAGE_TIMEOUT) {
                    logMessage("在职位详情页停留超过5秒，返回职位列表");
                    lastDetailPageTime = 0; // 重置计时器
                    performBackOperation();
                    return;
                }
                
                // 处理职位详情页面
                handleJobDetail(rootNode);
                break;
                
            case CHAT_PAGE:
                // 重置详情页计时器
                lastDetailPageTime = 0;
                // 处理聊天页面
                handleChatPage(rootNode);
                break;
        }
    }

    // 修改其他可能提到"重启"的方法
    private void handleStaticPage() {
        logMessage("检测到页面长时间未变化，执行返回操作");
        performBackOperation();
    }

    // 添加启动BOSS直聘APP的方法
    public static void launchBossApp(Context context) {
        try {
            // 方法1：通过包名启动BOSS直聘APP
            Intent intent = context.getPackageManager().getLaunchIntentForPackage(BOSS_PACKAGE_NAME);
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                return;
            }
            
            // 方法2：直接启动特定Activity
            Intent directIntent = new Intent();
            directIntent.setClassName(BOSS_PACKAGE_NAME, BOSS_MAIN_ACTIVITY);
            directIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(directIntent);
        } catch (Exception e) {
            Log.e(TAG, "启动BOSS直聘APP失败: " + e.getMessage());
        }
    }

    // 修改原有的findJobNodes方法，改进职位卡片查找逻辑
    private List<AccessibilityNodeInfo> findJobNodes(AccessibilityNodeInfo rootNode) {
        List<AccessibilityNodeInfo> jobNodes = new ArrayList<>();
        
        if (rootNode == null) return jobNodes;
        
        // 1. 先找到职位列表RecyclerView
        List<AccessibilityNodeInfo> rvList = rootNode.findAccessibilityNodeInfosByViewId("com.hpbr.bosszhipin:id/rv_list");
        if (!rvList.isEmpty() && rvList.get(0) != null) {
            AccessibilityNodeInfo recyclerView = rvList.get(0);
            logMessage("找到职位列表RecyclerView");
            
            // 2. 遍历RecyclerView的直接子节点
            for (int i = 0; i < recyclerView.getChildCount(); i++) {
                AccessibilityNodeInfo cardLayout = recyclerView.getChild(i);
                if (cardLayout != null) {
                    // 打印每个卡片的信息
                    List<AccessibilityNodeInfo> titleNodes = cardLayout.findAccessibilityNodeInfosByViewId("com.hpbr.bosszhipin:id/tv_position_name");
                    if (!titleNodes.isEmpty() && titleNodes.get(0) != null) {
                        String jobTitle = titleNodes.get(0).getText() != null ? 
                            titleNodes.get(0).getText().toString() : "未知职位";
                        logMessage("发现职位: " + jobTitle);
                        
                        // 如果卡片可点击，添加到列表
                        if (cardLayout.isClickable()) {
                            jobNodes.add(cardLayout);
                            logMessage("添加可点击的职位卡片: " + jobTitle);
                        } else {
                            logMessage("职位卡片不可点击，跳过: " + jobTitle);
                        }
                    } else {
                        logMessage("第 " + (i+1) + " 个卡片未找到职位名称");
                    }
                }
            }
        } else {
            logMessage("未找到职位列表RecyclerView，尝试查找单个职位卡片");
            
            // 3. 如果找不到列表，直接查找职位名称
            List<AccessibilityNodeInfo> allTitleNodes = rootNode.findAccessibilityNodeInfosByViewId("com.hpbr.bosszhipin:id/tv_position_name");
            for (AccessibilityNodeInfo titleNode : allTitleNodes) {
                if (titleNode != null && titleNode.getText() != null) {
                    String jobTitle = titleNode.getText().toString();
                    logMessage("直接查找到职位: " + jobTitle);
                    
                    // 向上查找可点击的父节点
                    AccessibilityNodeInfo parent = titleNode.getParent();
                    while (parent != null && !parent.isClickable()) {
                        parent = parent.getParent();
                    }
                    
                    if (parent != null && parent.isClickable()) {
                        jobNodes.add(parent);
                        logMessage("添加可点击的职位卡片(通过父节点): " + jobTitle);
                    }
                }
            }
        }
        
        logMessage("总共找到 " + jobNodes.size() + " 个职位卡片");
        return jobNodes;
    }
    
    // 处理职位列表页面 - 包含强制滑动和职位名称匹配
    private void handleMainList(AccessibilityNodeInfo rootNode) {
        if (rootNode == null) return;
        
        // 强制滑动 - 添加强制滑动逻辑，确保在职位列表页面能滑动
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastAutoScrollTime >= AUTO_SCROLL_INTERVAL) {
            logMessage("职位列表页面 - 执行强制滑动刷新");
            performScrollDown();  // 确保调用滑动方法
            lastAutoScrollTime = currentTime;
            
            // 重置滑动状态，避免被阻止滑动
            isScrolling = false;
            
            // 短暂延迟后再处理职位列表
            handler.postDelayed(() -> {
                // 延迟后查找职位节点
                AccessibilityNodeInfo newRoot = getRootInActiveWindow();
                if (newRoot != null) {
                    List<AccessibilityNodeInfo> jobNodes = findJobNodes(newRoot);
                    logMessage("滑动后找到 " + jobNodes.size() + " 个职位卡片");
                    processJobNodes(jobNodes);
                    newRoot.recycle();
                }
            }, 800);  // 给滑动一些完成的时间
            
            return;  // 滑动后直接返回，等待下一次检查
        }
        
        // 如果不需要滑动，直接处理职位列表
        List<AccessibilityNodeInfo> jobNodes = findJobNodes(rootNode);
        logMessage("找到 " + jobNodes.size() + " 个职位卡片");
        processJobNodes(jobNodes);
    }

    // 获取节点的文本内容
    private String getNodeText(AccessibilityNodeInfo node) {
        if (node == null) return null;
        
        // 尝试获取节点自身的文本
        CharSequence text = node.getText();
        if (text != null) {
            return text.toString();
        }
        
        // 如果节点自身没有文本，获取所有子节点的文本并合并
        List<String> texts = getAllTextsFromNode(node);
        if (!texts.isEmpty()) {
            return String.join(" ", texts);
        }
        
        // 如果没有找到任何文本，生成一个唯一标识
        return generateNodeId(node);
    }

    // 添加查找底部区域节点的方法
    private List<AccessibilityNodeInfo> findNodesInBottomArea(AccessibilityNodeInfo rootNode) {
        List<AccessibilityNodeInfo> bottomNodes = new ArrayList<>();
        if (rootNode == null) return bottomNodes;
        
        // 获取屏幕尺寸
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        
        // 遍历所有节点，找出位于屏幕底部区域的节点
        List<AccessibilityNodeInfo> allNodes = getAllNodesFromRoot(rootNode);
        for (AccessibilityNodeInfo node : allNodes) {
            Rect bounds = new Rect();
            node.getBoundsInScreen(bounds);
            
            // 检查节点是否在屏幕底部区域（底部20%的区域）
            if (bounds.bottom > screenHeight * 0.8) {
                bottomNodes.add(node);
            }
        }
        
        return bottomNodes;
    }

    // 添加performSimpleReturn方法
    private void performSimpleReturn(int times) {
        logMessage("执行简单返回操作，次数: " + times);
        
        // 检查返回次数是否合理
        if (times <= 0 || times > 3) {
            logMessage("返回次数不合理，取消操作");
            return;
        }
        
        // 执行第一次返回
        performGlobalAction(GLOBAL_ACTION_BACK);
        
        // 如果需要多次返回，延迟执行
        if (times > 1) {
            handler.postDelayed(() -> {
                performGlobalAction(GLOBAL_ACTION_BACK);
                
                // 如果需要第三次返回，再次延迟执行
                if (times > 2) {
                    handler.postDelayed(() -> {
                        performGlobalAction(GLOBAL_ACTION_BACK);
                    }, BACK_OPERATION_DELAY);
                }
            }, BACK_OPERATION_DELAY);
        }
        
        // 延迟后检查当前页面
        handler.postDelayed(() -> {
            AccessibilityNodeInfo rootNode = getRootInActiveWindow();
            if (rootNode != null) {
                PageType currentPage = getCurrentPageType(rootNode);
                logMessage("返回操作后当前页面: " + (currentPage != null ? currentPage.toString() : "未知"));
                
                if (currentPage == PageType.MAIN_LIST) {
                    // 如果返回到主界面，开始查找职位
                    logMessage("成功返回到主界面，开始查找职位");
                    handler.postDelayed(() -> findAndClickJobs(getRootInActiveWindow()), MAIN_PAGE_LOAD_DELAY);
                }
            }
        }, BACK_OPERATION_DELAY * times + 1000);
    }

    // 此方法已废弃，不再需要延迟检查
    private void delayedCheckDetailPage() {
        return;
    }
    
    // 修改滑动方法，彻底重写以确保工作正常
    private void scrollDown() {
        logMessage("开始执行滑动操作 - 紧急修复版");
        
        try {
        // 获取屏幕尺寸
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        
            // 创建一个明确的、更长的滑动路径
        Path path = new Path();
            path.moveTo(screenWidth / 2, screenHeight * 0.7f);  // 从屏幕70%位置开始
            path.lineTo(screenWidth / 2, screenHeight * 0.3f);  // 滑动到屏幕30%位置
        
            // 创建手势描述，使用更长的持续时间（700ms）
        GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
            GestureDescription.StrokeDescription stroke = new GestureDescription.StrokeDescription(path, 0, 700);
            gestureBuilder.addStroke(stroke);
            
            logMessage("执行滑动手势: 从 " + (screenHeight * 0.7f) + " 到 " + (screenHeight * 0.3f));
            
            // 直接执行手势，不使用回调
            dispatchGesture(gestureBuilder.build(), null, null);
            
            // 强制等待一段时间，确保滑动完成
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                logMessage("滑动等待被中断: " + e.getMessage());
            }
            
            logMessage("滑动操作完成");
        } catch (Exception e) {
            logMessage("滑动时发生异常: " + e.getMessage());
        }
    }

    // 修改isInMainPage方法，优化职位主界面检测
    private boolean isInMainPage(AccessibilityNodeInfo rootNode) {
        if (rootNode == null) return false;
        
        // 首先检查是否有"再按一次退出程序"提示
        List<AccessibilityNodeInfo> exitPromptNodes = rootNode.findAccessibilityNodeInfosByText("再按一次退出");
        if (!exitPromptNodes.isEmpty()) {
            logMessage("检测到退出提示，判断为职位主界面");
            return true;
        }
        
        // 检查是否在职位详情页 - 如果是职位详情页，则不是主界面
        List<AccessibilityNodeInfo> communicateNodes = rootNode.findAccessibilityNodeInfosByViewId("com.hpbr.bosszhipin:id/btn_chat");
        for (AccessibilityNodeInfo node : communicateNodes) {
            if (node.getText() != null) {
                String buttonText = node.getText().toString();
                if (buttonText.equals("立即沟通") || buttonText.equals("继续沟通")) {
                    // 这是职位详情页，不是主界面
                    return false;
                }
            }
        }
        
        // 通过文本查找"立即沟通"或"继续沟通"按钮
        List<AccessibilityNodeInfo> immediateNodes = rootNode.findAccessibilityNodeInfosByText("立即沟通");
        List<AccessibilityNodeInfo> continueNodes = rootNode.findAccessibilityNodeInfosByText("继续沟通");
        if (!immediateNodes.isEmpty() || !continueNodes.isEmpty()) {
            // 这是职位详情页，不是主界面
            return false;
        }
        
        // 检查是否有职位标签且被选中
        List<AccessibilityNodeInfo> tabNodes = rootNode.findAccessibilityNodeInfosByText("职位");
        for (AccessibilityNodeInfo node : tabNodes) {
            if (node.isSelected()) {
                logMessage("检测到底部职位标签被选中，判断为职位主界面");
                return true;
            }
        }
        
        // 检查是否有推荐/附近/最新标签
        List<AccessibilityNodeInfo> recommendNodes = rootNode.findAccessibilityNodeInfosByText("推荐");
        List<AccessibilityNodeInfo> nearbyNodes = rootNode.findAccessibilityNodeInfosByText("附近");
        List<AccessibilityNodeInfo> newestNodes = rootNode.findAccessibilityNodeInfosByText("最新");
        
        if (!recommendNodes.isEmpty() || !nearbyNodes.isEmpty() || !newestNodes.isEmpty()) {
            // 同时检查是否有职位列表
            List<AccessibilityNodeInfo> jobListNodes = findJobCards(rootNode);
            if (!jobListNodes.isEmpty()) {
                logMessage("检测到推荐/附近/最新标签和职位列表，判断为职位主界面");
                return true;
            }
        }
        
        return false;
    }

    // 添加检查是否在职位详情页的方法
    private boolean isInJobDetailPage(AccessibilityNodeInfo rootNode) {
        if (rootNode == null) return false;
        
        // 检查是否有"立即沟通"或"继续沟通"按钮
        List<AccessibilityNodeInfo> communicateNodes = rootNode.findAccessibilityNodeInfosByViewId("com.hpbr.bosszhipin:id/btn_chat");
        for (AccessibilityNodeInfo node : communicateNodes) {
            if (node.getText() != null) {
                String buttonText = node.getText().toString();
                if (buttonText.equals("立即沟通") || buttonText.equals("继续沟通")) {
                    logMessage("检测到" + buttonText + "按钮，判断为职位详情页");
                    return true;
                }
            }
        }
        
        // 通过文本查找"立即沟通"或"继续沟通"按钮
        List<AccessibilityNodeInfo> immediateNodes = rootNode.findAccessibilityNodeInfosByText("立即沟通");
        List<AccessibilityNodeInfo> continueNodes = rootNode.findAccessibilityNodeInfosByText("继续沟通");
        if (!immediateNodes.isEmpty() || !continueNodes.isEmpty()) {
            logMessage("检测到立即沟通/继续沟通按钮，判断为职位详情页");
            return true;
        }
        
        // 检查是否有职位描述、公司介绍等文字
        List<AccessibilityNodeInfo> jobDescNodes = rootNode.findAccessibilityNodeInfosByText("职位描述");
        List<AccessibilityNodeInfo> companyIntroNodes = rootNode.findAccessibilityNodeInfosByText("公司介绍");
        if (!jobDescNodes.isEmpty() && !companyIntroNodes.isEmpty()) {
            logMessage("检测到职位描述和公司介绍文本，判断为职位详情页");
            return true;
        }
        
        return false;
    }

    // 添加服务看门狗相关变量
    private Handler serviceWatchdogHandler = new Handler(Looper.getMainLooper());
    private Runnable serviceWatchdogRunnable;
    private static final long SERVICE_WATCHDOG_INTERVAL = 5000; // 每5秒检查一次服务状态

    // 增加一个标记，记录当前处理的职位ID，避免重复计数
    private String currentJobId = "";
    private boolean hasCountedCurrentJob = false;








    
    // 发送通知提醒
    private void sendNotification(String title, String content) {
        // 简单记录日志，实际上这里可以实现发送系统通知
        logMessage("系统通知: " + title + " - " + content);
    }

    // 设置一个调试开关变量
    private static final boolean DEBUG_LOG_ALL_NODES = false; // 设置为false关闭详细日志
    
    // 修改matchesKeywords方法，使其更健壮
    private boolean matchesKeywords(String jobTitle) {
        if (jobTitle == null || jobTitle.isEmpty() || keywords.isEmpty()) {
            return false;
        }
        
        jobTitle = jobTitle.toLowerCase();
        for (String keyword : keywords) {
            if (keyword != null && !keyword.isEmpty() && jobTitle.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    // 启动定期检查APP状态的机制
    private void startAppStatusCheck() {
        logMessage("启动应用状态检查");
        
        if (appStatusCheckRunnable != null) {
            appStatusCheckHandler.removeCallbacks(appStatusCheckRunnable);
        }
        
        appStatusCheckRunnable = new Runnable() {
            @Override
            public void run() {
                // 如果服务正在停止，不继续检查
                if (isServiceStopping) {
                    logMessage("服务正在停止，取消应用状态检查");
                    return;
                }
                
                // 不再自动重启服务
                
                // 检查BOSS直聘是否在运行
                if (isRunning) {
                    checkIfBossAppRunning();
                }
                
                // 安排下次检查
                if (isRunning && !isServiceStopping) {
                    appStatusCheckHandler.postDelayed(this, APP_STATUS_CHECK_INTERVAL);
                }
            }
        };
        
        // 开始第一次检查
        appStatusCheckHandler.post(appStatusCheckRunnable);
    }
    
    // 停止定期检查
    private void stopAppStatusCheck() {
        if (appStatusCheckRunnable != null) {
            appStatusCheckHandler.removeCallbacks(appStatusCheckRunnable);
            logMessage("已停止应用状态检查");
        }
    }
    


    // 添加一个强制停止服务的方法，不检查打招呼次数
    private void forceStopService() {
        if (isServiceStopping) {
            logMessage("服务正在停止中，跳过重复停止");
            return;
        }
        
        isServiceStopping = true;
        logMessage("BOSS直聘已退出，强制停止自动投递服务，共投递 " + totalCount + " 个岗位，打招呼 " + greetingCount + " 次");
        
        // 停止应用状态检查
        stopAppStatusCheck();
        
        // 取消所有延迟任务
        handler.removeCallbacksAndMessages(null);
        mainHandler.removeCallbacksAndMessages(null);
        appStatusCheckHandler.removeCallbacksAndMessages(null);
        serviceWatchdogHandler.removeCallbacksAndMessages(null);
        
        // 清理所有待执行的任务
        for (Runnable task : pendingTasks) {
            handler.removeCallbacks(task);
            mainHandler.removeCallbacks(task);
        }
        pendingTasks.clear();
        
        // 重置所有状态标志
        isScrolling = false;
        isTabClickPending = false;
        consecutiveBackCount = 0;
        currentPageType = null;
        previousPageType = null;
        
        // 发送广播通知MainActivity更新UI
        Intent intent = new Intent(MainActivity.ACTION_SERVICE_STATUS_CHANGED);
        intent.putExtra("running", false);
        intent.putExtra("count", totalCount);
        sendBroadcast(intent);
        
        // 真正设置服务状态为停止
        isRunning = false;
        isServiceStopping = false; // 重置停止标志，以便下次可以正常启动
        
        // 最终确保所有操作都停止
        handler.postDelayed(() -> {
            // 再次检查是否有任何任务在运行
            handler.removeCallbacksAndMessages(null);
            mainHandler.removeCallbacksAndMessages(null);
            logMessage("所有任务已彻底清理，服务已完全停止");
        }, 1000);
    }

    // 修改postDelayed方法，跟踪所有的延迟任务
    private void postDelayedTask(Runnable runnable, long delayMillis) {
        if (!isRunning) return; // 如果服务已停止，不再添加新任务
        
        pendingTasks.add(runnable);
        handler.postDelayed(() -> {
            if (isRunning) { // 再次检查服务是否还在运行
                runnable.run();
            }
            pendingTasks.remove(runnable);
        }, delayMillis);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // 确保所有计时器和回调都被清理
        if (appStatusCheckRunnable != null) {
            appStatusCheckHandler.removeCallbacks(appStatusCheckRunnable);
        }
        
        if (serviceWatchdogRunnable != null) {
            serviceWatchdogHandler.removeCallbacks(serviceWatchdogRunnable);
        }
        
        // 确保服务状态被正确设置
        isRunning = false;
        isServiceStopping = false;
        
        logMessage("服务已完全停止");
        
        // 停止界面检测
        checkHandler.removeCallbacks(pageCheckRunnable);
    }

    // 添加两个缺失的方法
    // 处理聊天页面检测
    private void handleChatPageDetected() {
        // 添加标记，避免短时间内重复处理
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastBackOperationTime < 5000) {
            logMessage("短时间内已处理过聊天页面，跳过此次处理");
            return;
        }
        
        // 更新最后处理时间
        lastBackOperationTime = currentTime;
        
        logMessage("检测到聊天页面，执行返回操作");
        
        // 增加打招呼计数
        greetingCount++;
        logMessage("当前已打招呼次数: " + greetingCount + "/" + MAX_GREETING_COUNT);
        
        // 检查是否达到最大打招呼次数
        if (greetingCount >= MAX_GREETING_COUNT && !isServiceStopping) {
            logMessage("已达到最大打招呼次数 " + MAX_GREETING_COUNT + "，准备停止服务");
            handler.postDelayed(() -> {
                stopService();
            }, 2000);
            return;
        }
        
        // 执行返回操作
        performBackOperation();
    }
    
    // 这是监控服务状态的方法，让我们确保它不会自动重启服务
    private void startPeriodicCheck() {
        logMessage("启动应用状态检查和页面定期检查");
        
        // 启动应用状态检查（已存在的方法）
        startAppStatusCheck();
    }

    // 执行返回操作 - 修复中文引号导致的编译错误
    private void performBackOperation() {
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode != null) {
            PageType currentPage = getCurrentPageType(rootNode);
            
            // 如果当前在职位详情页面，检查是否满足最小等待时间
            if (currentPage == PageType.JOB_DETAIL) {
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastStateChangeTime < 5000) { // 至少等待5秒
                    logMessage("职位详情页面尚未处理完成，取消返回操作");
                    rootNode.recycle();
                    return;
                }
            }
            
            // 如果当前在主界面，不执行返回操作
            if (currentPage == PageType.MAIN_LIST) {
                logMessage("当前已在主界面，不执行返回操作");
                rootNode.recycle();
                return;
            }
            
            rootNode.recycle();
        }
        
        // 执行返回操作前记录返回时间
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastBackOperationTime < MIN_BACK_INTERVAL) {
            logMessage("返回操作间隔过短，延迟执行返回");
            handler.postDelayed(this::performBackOperation, MIN_BACK_INTERVAL);
            return;
        }
        
        // 更新最后返回时间并执行返回
        lastBackOperationTime = currentTime;
        logMessage("执行返回操作");
        performGlobalAction(GLOBAL_ACTION_BACK);
    }

    // 添加连续检测计数
    private int bossAppExitDetectionCount = 0;
    private static final int REQUIRED_EXIT_DETECTIONS = 5; // 增加连续检测次数要求
    private long lastExitCheckTime = 0;
    private static final long EXIT_CHECK_INTERVAL = 3000; // 增加退出检查间隔
    private long lastUserInteractionTime = 0; // 记录最后用户交互时间
    private static final long MIN_EXIT_AFTER_INTERACTION = 10000; // 交互后至少10秒才允许退出

    // 改进检查BOSS直聘是否还在运行的方法，减少误判
    private void checkIfBossAppRunning() {
        // 如果服务正在停止，直接返回
        if (isServiceStopping || !isRunning) {
            return;
        }
        
        // 默认假设BOSS仍在运行，除非明确检测到它已退出
        boolean isBossRunning = true;
        long currentTime = System.currentTimeMillis();
        
        // 如果两次检查间隔太短，跳过此次检查
        if (currentTime - lastExitCheckTime < EXIT_CHECK_INTERVAL) {
            return;
        }
        
        lastExitCheckTime = currentTime;
        
        // 如果最近有用户交互，延迟退出判断
        if (currentTime - lastUserInteractionTime < MIN_EXIT_AFTER_INTERACTION) {
            logMessage("检测到最近有用户交互，延迟退出判断");
            bossAppExitDetectionCount = 0; // 重置计数
            return;
        }
        
        // 获取当前窗口信息
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode != null) {
            CharSequence packageName = rootNode.getPackageName();
            // 如果能获取到窗口，且包名是BOSS直聘，则肯定在运行
            if (packageName != null && packageName.toString().equals(BOSS_PACKAGE_NAME)) {
                // 如果检测到BOSS正在运行，重置计数
                if (bossAppExitDetectionCount > 0) {
                    logMessage("检测到BOSS直聘仍在运行，重置退出检测计数");
                    bossAppExitDetectionCount = 0;
                }
            } else {
                // 即使当前窗口不是BOSS直聘，也不要马上认为它不在运行
                // 只是记录情况，但不改变isBossRunning的值（保持为true）
                if (packageName != null) {
                    logMessage("当前前台窗口不是BOSS直聘，而是: " + packageName.toString() + "，但这不意味着BOSS直聘已退出");
                }
            }
            rootNode.recycle();
        } else {
            // 检查是否正在活跃使用，如果是则不应该显示此消息
            if (System.currentTimeMillis() - lastUserInteractionTime > 30000 && 
                System.currentTimeMillis() - lastStateChangeTime > 30000) {
                logMessage("页面可能处于过渡状态，暂时无法获取窗口信息");
            }
            
            // 检查是否有最近的活动迹象
            if (isRecentlyActive()) {
                // 如果最近有活动，几乎可以确定应用仍在运行
                isBossRunning = true;
                return; // 直接返回，不增加退出计数
            }
        }
        
        // 只有在经过多项检查确认BOSS直聘确实可能不在运行时，才考虑增加计数
        // 由于无法完全确定BOSS直聘是否在后台运行，我们将非常保守地增加计数
        // 添加额外的检查来确认是否真的退出了
        if (!isBossRunning && isRunning && !isRecentlyActive()) {
            // 再次尝试使用其他方法确认BOSS是否真的不在运行
            boolean confirmExit = isAppReallyExited();
            
            if (confirmExit) {
                // 再次确认BOSS直聘是否真的没有运行
                try {
                    // 再次检查BOSS应用是否安装
                    getPackageManager().getPackageInfo(BOSS_PACKAGE_NAME, 0);
                    
                    // 如果执行到这里，表示BOSS应用已安装但可能没在前台
                    // 我们减少误判的可能性
                    logMessage("BOSS直聘似乎不在活动状态 - 谨慎检测中");
                    // 增加更严格的条件: 只有连续5次检测且间隔较长才计数一次
                    if (bossAppExitDetectionCount % 5 == 0 && currentTime - lastExitCountIncreaseTime > 20000) {
                        bossAppExitDetectionCount++;
                        lastExitCountIncreaseTime = currentTime; 
                        logMessage("谨慎模式：未检测到BOSS直聘活动，退出检测计数: " + bossAppExitDetectionCount + "/" + REQUIRED_EXIT_DETECTIONS);
                    }
                } catch (PackageManager.NameNotFoundException e) {
                    // BOSS直聘真的未安装，直接增加计数
                    bossAppExitDetectionCount++;
                    logMessage("确认BOSS直聘未安装，退出检测计数: " + bossAppExitDetectionCount + "/" + REQUIRED_EXIT_DETECTIONS);
                }
            } else {
                // 如果额外检查表明BOSS可能仍在运行，重置计数
                if (bossAppExitDetectionCount > 0) {
                    logMessage("额外检查显示BOSS直聘可能仍在运行，重置退出检测计数");
                    bossAppExitDetectionCount = 0;
                }
            }
        }
    }

    // 改进isAppReallyExited方法，减少误判
    private boolean isAppReallyExited() {
        try {
            // 检查是否有任何BOSS直聘相关的活动
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null) {
                // 检查页面中是否有任何BOSS直聘的元素
                boolean hasBossElements = false;
                
                // 检查特定的BOSS直聘UI元素
                List<String> bossElementIds = Arrays.asList(
                    "com.hpbr.bosszhipin:id/tv_tab_text",
                    "com.hpbr.bosszhipin:id/btn_chat",
                    "com.hpbr.bosszhipin:id/iv_chat"
                );
                
                for (String id : bossElementIds) {
                    List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(id);
                    if (!nodes.isEmpty()) {
                        hasBossElements = true;
                        break;
                    }
                }
                
                root.recycle();
                
                if (hasBossElements) {
                    logMessage("检测到BOSS直聘的UI元素，应用可能仍在运行");
                    return false;
                }
            }
            
            // 如果最近有用户交互或窗口变化，几乎可以确定应用仍在运行
            if (System.currentTimeMillis() - lastUserInteractionTime < 30000 || 
                System.currentTimeMillis() - lastStateChangeTime < 30000) {
                logMessage("最近有用户交互或窗口变化，BOSS直聘应该仍在运行");
                return false;
            }
            
            // 如果以上所有检查都通过，再谨慎地考虑应用可能已退出
            return false;
        } catch (Exception e) {
            logMessage("检查应用状态时发生异常: " + e.getMessage());
            return false;
        }
    }
    
    // 辅助方法: 检查是否最近有活动迹象
    private boolean isRecentlyActive() {
        long currentTime = System.currentTimeMillis();
        
        // 如果最近有用户交互，认为应用仍在活动中
        if (currentTime - lastUserInteractionTime < 30000) { // 30秒内有交互
            return true;
        }
        
        // 如果最近有检测到窗口变化，认为应用仍在活动中
        if (currentTime - lastStateChangeTime < 20000) { // 20秒内有窗口变化
            return true; 
        }
        
        return false;
    }
    
    // 修改其他地方包含"重启"的代码
    private void checkCurrentAppStatus() {
        AccessibilityNodeInfo currentRootNode = getRootInActiveWindow();
        if (currentRootNode != null) {
            if (currentRootNode.getPackageName() == null || 
                !currentRootNode.getPackageName().toString().equals(BOSS_PACKAGE_NAME)) {
                logMessage("检测到已退出BOSS直聘，请手动启动应用");
                return;
            }
        }
    }



    // 修改查找节点失败处理方法
    private void handleNodeFindingFailure() {
        logMessage("多次无法找到目标节点，返回上一页");
        performBackOperation();
    }

    // 修改returnToMainList方法
    private void returnToMainList() {
        logMessage("尝试返回到主列表页面");
        // 通过返回操作回到主页面
        forceNavigateBack();
    }

    // 移除任何可能调用restartBossApp的代码
    private void handleAppExit() {
        logMessage("检测到应用退出，请手动启动BOSS直聘");
    }



    // 修改服务检查逻辑，移除所有重启尝试
    private void serviceWatchdogCheck() {
        // 尝试恢复操作
        try {
            AccessibilityNodeInfo rootNode = getRootInActiveWindow();
            if (rootNode != null) {
                PageType currentPage = getCurrentPageType(rootNode);
                if (currentPage != null) {
                    handlePageByType(currentPage, rootNode);
                } else {
                    // 无法识别页面类型，只记录日志
                    logMessage("无法识别当前页面类型，不执行任何操作");
                }
            } else {
                // 无法获取窗口信息，只记录日志
                logMessage("无法获取当前窗口信息，不执行任何操作");
            }
        } catch (Exception e) {
            logMessage("恢复服务时发生异常: " + e.getMessage());
            // 发生异常，只记录日志
            logMessage("服务检查过程中发生异常，不执行任何操作");
        }
    }

    // 添加缺失的collectChildNodes方法
    // 递归收集子节点
    private void collectChildNodes(AccessibilityNodeInfo node, List<AccessibilityNodeInfo> children) {
        if (node == null) return;
        
        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                children.add(child);
                collectChildNodes(child, children);
            }
        }
    }

    // 添加缺失的handleJobDetail方法
    // 处理职位详情页面
    private void handleJobDetail(AccessibilityNodeInfo rootNode) {
        if (rootNode == null) return;
        
        // 首先检查已点击沟通但长时间未成功进入聊天页面的情况
        if (hasCommunicateClicked) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastCommunicateClickTime > COMMUNICATE_TIMEOUT) {
                logMessage("点击沟通按钮后5秒内未成功进入聊天页面，执行返回操作");
                hasCommunicateClicked = false; // 重置标记
                performBackOperation(); // 执行返回操作
                return;
            }
        }

        // 先检查是否有"继续沟通"按钮
        List<AccessibilityNodeInfo> continueButtons = rootNode.findAccessibilityNodeInfosByText("继续沟通");
        if (!continueButtons.isEmpty()) {
            logMessage("检测到继续沟通按钮，说明已经沟通过，直接返回职位列表");
            performBackOperation();
            return;
        }
        
        // 查找"立即沟通"按钮
        List<AccessibilityNodeInfo> chatButtons = rootNode.findAccessibilityNodeInfosByText("立即沟通");
        if (!chatButtons.isEmpty()) {
            for (AccessibilityNodeInfo button : chatButtons) {
                if (button.isClickable()) {
                    logMessage("找到立即沟通按钮，点击开始沟通");
                    clickNode(button);
                    // 记录点击时间和状态
                    lastCommunicateClickTime = System.currentTimeMillis();
                    hasCommunicateClicked = true;
                    
                    // 设置5秒后的超时检查
                    handler.postDelayed(() -> {
                        // 如果5秒后仍然在职位详情页面，说明没有成功进入聊天
                        if (hasCommunicateClicked) {  // 如果标记仍然为true，说明没有成功进入聊天页面
                            logMessage("沟通按钮点击5秒后仍未进入聊天，返回职位列表");
                            hasCommunicateClicked = false;
                            performBackOperation();
                        }
                    }, COMMUNICATE_TIMEOUT);
                    return;
                }
            }
        }
    }

    // 添加缺失的handleChatPage方法
    // 处理聊天页面
    private void handleChatPage(AccessibilityNodeInfo rootNode) {
        if (rootNode == null) return;
        
        // 查找输入框
        List<AccessibilityNodeInfo> inputBoxes = rootNode.findAccessibilityNodeInfosByViewId("com.hpbr.bosszhipin:id/et_chat");
        if (!inputBoxes.isEmpty()) {
            AccessibilityNodeInfo inputBox = inputBoxes.get(0);
            
            // 设置打招呼文本
            Bundle arguments = new Bundle();
            arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, GREETING_MESSAGE);
            inputBox.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
            
            // 查找发送按钮
            List<AccessibilityNodeInfo> sendButtons = rootNode.findAccessibilityNodeInfosByViewId("com.hpbr.bosszhipin:id/btn_send");
            if (!sendButtons.isEmpty()) {
                logMessage("发送打招呼消息: " + GREETING_MESSAGE);
                clickNode(sendButtons.get(0));
                
                // 标记已发送打招呼消息
                greetingSent = true;
                
                // 延迟2秒后执行一次返回
                handler.postDelayed(() -> {
                    performBackOperation();  // 执行返回
                }, 2000);
            }
        } else {
            logMessage("未找到聊天输入框，尝试返回");
            // 立即执行一次返回
            performBackOperation();
        }
    }

    // 处理restartBossApp引用，因为我们已经移除了这个方法
    // 在checkCurrentAppStatus方法中

    // 空方法，替代所有对重启的调用
    private void restartBossApp() {
        logMessage("检测到调用重启功能，但重启功能已被禁用");
        // 不执行任何重启操作
    }

    // 增强检测聊天限制弹窗的方法
    private boolean checkDailyLimitReached(AccessibilityNodeInfo rootNode) {
        if (rootNode == null) return false;
        
        // 获取当前窗口的包名，判断是否是BOSS直聘应用的弹窗
        String packageName = null;
        if (rootNode.getPackageName() != null) {
            packageName = rootNode.getPackageName().toString();
            if (!BOSS_PACKAGE_NAME.equals(packageName)) {
                // 如果不是BOSS直聘应用的窗口，直接返回
                return false;
            }
        }
        
        // 检查弹窗类型，判断是APP自定义弹窗还是原生系统弹窗
        boolean isSystemDialog = false;
        if (rootNode.getClassName() != null) {
            String className = rootNode.getClassName().toString();
            if (className.equals("android.app.AlertDialog") || className.equals("android.widget.Toast")) {
                isSystemDialog = true;
                logMessage("检测到原生系统弹窗");
            }
        }
        
        // 检查是否有系统原生对话框的特征元素
        List<AccessibilityNodeInfo> systemTitleNodes = rootNode.findAccessibilityNodeInfosByViewId("android:id/alertTitle");
        if (!systemTitleNodes.isEmpty()) {
            isSystemDialog = true;
            logMessage("检测到系统原生AlertDialog弹窗");
        }
        
        // 检查整个窗口的所有文本内容
        List<AccessibilityNodeInfo> allTextNodes = new ArrayList<>();
        findAllTextNodes(rootNode, allTextNodes);
        
        // 专门检查"您今日聊得太多，休息一下明天再来吧~"这种格式的弹窗
        for (AccessibilityNodeInfo node : allTextNodes) {
            if (node.getText() != null) {
                String text = node.getText().toString();
                // 完整匹配弹窗文本
                if (text.contains("您今日聊得太多") && text.contains("休息一下明天再来")) {
                    logMessage("完全匹配到聊天限制弹窗: " + text + " (来源:" + (isSystemDialog ? "系统弹窗" : "应用弹窗") + ")");
                    return true;
                }
            }
        }
        
        // 直接检查完整的文本，增加匹配概率
        List<AccessibilityNodeInfo> fullTextNodes = rootNode.findAccessibilityNodeInfosByText("您今日聊得太多，休息一下明天再来");
        if (!fullTextNodes.isEmpty()) {
            logMessage("检测到完整的聊天限制提示");
            return true;
        }
        
        // 使用更短的特定文本片段检测
        String[] limitTexts = {
            "您今日聊得太多",
            "休息一下明天再来",
            "休息一下明天", // 添加更多可能的文本片段
            "聊得太多，休息",
            "聊得太多",
            "明天再来"
        };
        
        for (String text : limitTexts) {
            List<AccessibilityNodeInfo> nodes = rootNode.findAccessibilityNodeInfosByText(text);
            if (!nodes.isEmpty()) {
                logMessage("检测到聊天限制提示文本: " + text);
                return true;
            }
        }
        
        // 添加对话框标题检测
        List<AccessibilityNodeInfo> dialogTitleNodes = rootNode.findAccessibilityNodeInfosByViewId("com.hpbr.bosszhipin:id/dialog_title");
        if (!dialogTitleNodes.isEmpty()) {
            for (AccessibilityNodeInfo node : dialogTitleNodes) {
                if (node.getText() != null && node.getText().toString().contains("今日聊天")) {
                    logMessage("检测到聊天限制对话框标题");
                    return true;
                }
            }
        }
        
        // 尝试检测对话框内容
        List<AccessibilityNodeInfo> dialogContentNodes = rootNode.findAccessibilityNodeInfosByViewId("com.hpbr.bosszhipin:id/dialog_content");
        if (!dialogContentNodes.isEmpty()) {
            for (AccessibilityNodeInfo node : dialogContentNodes) {
                if (node.getText() != null && 
                    (node.getText().toString().contains("聊得太多") || 
                     node.getText().toString().contains("明天再来"))) {
                    logMessage("检测到聊天限制对话框内容");
                    return true;
                }
            }
        }
        
        // 检查图片中显示的弹窗特定样式
        List<AccessibilityNodeInfo> darkOverlays = findNodesByClassName(rootNode, "android.widget.FrameLayout");
        for (AccessibilityNodeInfo overlay : darkOverlays) {
            // 检查是否是覆盖整个屏幕的暗色背景
            Rect bounds = new Rect();
            overlay.getBoundsInScreen(bounds);
            
            // 如果是全屏覆盖的暗色背景，检查其子元素是否包含相关文本
            if (bounds.width() > screenWidth * 0.8 && bounds.height() > screenHeight * 0.8) {
                List<AccessibilityNodeInfo> childTextNodes = new ArrayList<>();
                findAllTextNodes(overlay, childTextNodes);
                
                for (AccessibilityNodeInfo textNode : childTextNodes) {
                    if (textNode.getText() != null && 
                        (textNode.getText().toString().contains("聊得太多") || 
                         textNode.getText().toString().contains("明天再来"))) {
                        logMessage("在全屏暗色背景中检测到聊天限制文本");
                        return true;
                    }
                }
            }
        }
        
        return false;
    }

    // 辅助方法：查找所有文本节点
    private void findAllTextNodes(AccessibilityNodeInfo node, List<AccessibilityNodeInfo> textNodes) {
        if (node == null) return;
        
        if (node.getText() != null) {
            textNodes.add(node);
        }
        
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                findAllTextNodes(child, textNodes);
            }
        }
    }
    


    // 修改检测到聊天限制后的处理逻辑，确保成功返回到职位界面
    private void handleDailyLimitReached() {
        logMessage("检测到聊天限制，准备返回职位界面");
        dailyLimitReached = true;
        
        // 首先尝试点击返回按钮（如果有的话）
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode != null) {
            // 查找可能的"确定"或"关闭"按钮
            List<AccessibilityNodeInfo> buttons = rootNode.findAccessibilityNodeInfosByText("确定");
            if (buttons.isEmpty()) {
                buttons = rootNode.findAccessibilityNodeInfosByText("关闭");
            }
            
            // 查找可能的"我知道了"按钮，这在某些弹窗中很常见
            if (buttons.isEmpty()) {
                buttons = rootNode.findAccessibilityNodeInfosByText("我知道了");
            }
            
            // 在BOSS直聘应用中，弹窗通常是私有实现，尝试寻找任何可点击的按钮
            if (buttons.isEmpty()) {
                List<AccessibilityNodeInfo> allClickableNodes = findAllClickableNodes(rootNode);
                for (AccessibilityNodeInfo node : allClickableNodes) {
                    if (node.getText() != null && !node.getText().toString().isEmpty()) {
                        buttons.add(node);
                    }
                }
            }
            
            if (!buttons.isEmpty()) {
                for (AccessibilityNodeInfo button : buttons) {
                    if (button.isClickable()) {
                        logMessage("点击弹窗上的按钮关闭提示: " + 
                                 (button.getText() != null ? button.getText() : "未知按钮"));
                        clickNode(button);
                        
                        // 延迟后执行返回操作，确保回到职位界面
                        handler.postDelayed(this::performBackOperation, 1000);
                        return;
                    }
                }
            }
            
            rootNode.recycle();
        }
        
        // 如果没找到按钮，直接执行返回操作
        performBackOperation();
        
        // 确保返回到职位界面后，再次检查并尝试点击职位标签
        handler.postDelayed(() -> {
            AccessibilityNodeInfo newRoot = getRootInActiveWindow();
            if (newRoot != null) {
                logMessage("检查是否已返回到职位界面");
                clickPositionTab(newRoot);
                newRoot.recycle();
            }
        }, 2000);
    }

    // 辅助方法：找出所有可点击的节点
    private List<AccessibilityNodeInfo> findAllClickableNodes(AccessibilityNodeInfo root) {
        List<AccessibilityNodeInfo> result = new ArrayList<>();
        if (root == null) return result;
        
        if (root.isClickable()) {
            result.add(root);
        }
        
        for (int i = 0; i < root.getChildCount(); i++) {
            AccessibilityNodeInfo child = root.getChild(i);
            if (child != null) {
                result.addAll(findAllClickableNodes(child));
            }
        }
        
        return result;
    }

    // 添加BOSS直聘退出检测相关变量
    private long lastExitCountIncreaseTime = 0; // 上次退出计数增加的时间

    // 添加变量跟踪是否在聊天页面
    private boolean inChatRoom = false;
    
    // 更积极地检测聊天限制弹窗
    private void checkForLimitPopupAggressively(AccessibilityNodeInfo rootNode) {
        if (rootNode == null) return;
        
        // 打印所有文本节点内容，帮助调试
        List<AccessibilityNodeInfo> allTextNodes = new ArrayList<>();
        findAllTextNodes(rootNode, allTextNodes);
        
        logMessage("聊天页面文本节点数量: " + allTextNodes.size());
        
        for (AccessibilityNodeInfo node : allTextNodes) {
            if (node.getText() != null) {
                String text = node.getText().toString();
                logMessage("检查文本: " + text);
                
                // 使用更宽松的匹配条件
                if (text.contains("太多") || 
                    text.contains("明天") || 
                    text.contains("休息") || 
                    text.contains("聊天") && text.contains("限制")) {
                    
                    logMessage("===检测到可能的限制文本: " + text + "===");
                    
                    // 立即处理
                    dailyLimitReached = true;
                    handler.postDelayed(this::handleDailyLimitReached, 500);
                    return;
                }
            }
        }
        
        // 检查弹窗特定的UI元素
        List<AccessibilityNodeInfo> possibleDialogs = findNodesByClassName(rootNode, "android.widget.LinearLayout");
        for (AccessibilityNodeInfo dialog : possibleDialogs) {
            // 检查是否有弹窗的典型特征：居中、较小尺寸
            Rect bounds = new Rect();
            dialog.getBoundsInScreen(bounds);
            
            // 检查是否是居中的小窗口
            if (bounds.width() > screenWidth * 0.3 && 
                bounds.width() < screenWidth * 0.9 &&
                bounds.height() > screenHeight * 0.1 && 
                bounds.height() < screenHeight * 0.7) {
                
                // 寻找其中的按钮
                List<AccessibilityNodeInfo> buttons = findNodesByClassName(dialog, "android.widget.Button");
                if (!buttons.isEmpty()) {
                    logMessage("检测到可能的弹窗UI元素");
                    
                    // 处理对话框内容
                    List<AccessibilityNodeInfo> dialogTextNodes = new ArrayList<>();
                    findAllTextNodes(dialog, dialogTextNodes);
                    
                    StringBuilder dialogText = new StringBuilder();
                    for (AccessibilityNodeInfo textNode : dialogTextNodes) {
                        if (textNode.getText() != null) {
                            dialogText.append(textNode.getText().toString()).append(" ");
                        }
                    }
                    
                    logMessage("弹窗内容: " + dialogText.toString());
                    
                    // 如果文本包含关键词，触发处理
                    String fullText = dialogText.toString();
                    if (fullText.contains("太多") || 
                        fullText.contains("明天") || 
                        fullText.contains("休息")) {
                        
                        logMessage("===在弹窗UI中检测到限制相关文本===");
                        dailyLimitReached = true;
                        handler.postDelayed(this::handleDailyLimitReached, 500);
                        return;
                    }
                }
            }
        }
    }

    // 添加跟踪变量
    private long lastCommunicateClickTime = 0; // 上次点击立即沟通按钮的时间
    private boolean hasCommunicateClicked = false; // 标记是否已点击沟通按钮
    private static final int COMMUNICATE_TIMEOUT = 5000; // 沟通超时时间设置为5秒

    // 强制返回方法，确保能返回到上一页面
    private void forceNavigateBack() {
        // 首先尝试使用系统返回按钮
        performBackOperation();
        
        // 延迟检查返回是否成功
        handler.postDelayed(() -> {
            // 获取当前页面类型
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null) {
                // 如果仍在同一页面，尝试查找并点击返回按钮
                List<AccessibilityNodeInfo> backButtons = root.findAccessibilityNodeInfosByViewId("com.hpbr.bosszhipin:id/iv_back");
                if (!backButtons.isEmpty()) {
                    for (AccessibilityNodeInfo backButton : backButtons) {
                        if (backButton.isClickable()) {
                            logMessage("点击返回按钮");
                            clickNode(backButton);
                            break;
                        }
                    }
                }
                root.recycle();
            }
        }, 1000);
    }

    // 添加新的变量来跟踪页面状态
    private long lastDetailPageTime = 0; // 进入职位详情页的时间
    private static final int DETAIL_PAGE_TIMEOUT = 5000; // 职位详情页停留超时时间（5秒）

    // 添加新的变量
    private Handler checkHandler = new Handler(Looper.getMainLooper());
    private Runnable pageCheckRunnable;
    private static final int CHECK_INTERVAL = 1000; // 每秒检查一次界面状态

    // 添加变量记录最后一次窗口类名
    private String lastWindowClassName = null;

    @Override
    public void onCreate() {
        super.onCreate();
        // 初始化界面检测定时器
        pageCheckRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isServiceStopping && isRunning) {
                    checkCurrentPage();
                    // 继续下一次检查
                    checkHandler.postDelayed(this, CHECK_INTERVAL);
                }
            }
        };
        // 立即开始界面检测
        checkHandler.post(pageCheckRunnable);
    }

    // 修改检查当前页面的方法
    private void checkCurrentPage() {
        if (isServiceStopping || !isRunning) return;

        // 尝试多种方式获取页面信息
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        List<String> pageTexts = new ArrayList<>();
        
        // 如果无法通过getRootInActiveWindow获取，尝试从当前窗口获取信息
        if (rootNode == null) {
            // 获取当前窗口的所有节点
            List<AccessibilityWindowInfo> windows = getWindows();
            if (windows != null) {
                for (AccessibilityWindowInfo window : windows) {
                    AccessibilityNodeInfo root = window.getRoot();
                    if (root != null) {
                        pageTexts.addAll(getAllTextsFromPage(root));
                        root.recycle();
                    }
                }
            }
        } else {
            pageTexts = getAllTextsFromPage(rootNode);
            rootNode.recycle();
        }

        // 根据页面文字特征判断页面类型
        PageType currentPage = detectPageTypeByTexts(pageTexts);
        
        // 如果刚检测到进入聊天页面，但当前页面类型为null，则当作职位详情页处理
        if (currentPage == null && lastWindowClassName != null && 
            lastWindowClassName.contains("ChatRoomActivity")) {
            logMessage("检测到可能是聊天限制导致的返回，当作职位详情页处理");
            currentPage = PageType.JOB_DETAIL;
        }
        
        // 如果没有检测到任何已知页面类型，执行返回操作并跳出循环
        if (currentPage == null) {
            logMessage("未检测到任何已知页面类型，执行返回操作");
            performBackOperation();
            return;
        }
        
        // 打印当前页面状态
        String pageStatus = "";  // 初始化变量
        switch (currentPage) {
            case MAIN_LIST:
                pageStatus = "职位列表主页面";
                break;
            case JOB_DETAIL:
                pageStatus = "职位详情页面";
                break;
            case CHAT_PAGE:
                pageStatus = "聊天页面";
                break;
        }
        logMessage("【界面检测】当前所在页面：" + pageStatus);
        
        // 如果在职位详情页面
        if (currentPage == PageType.JOB_DETAIL) {
            // 如果是首次进入详情页或从其他页面重新进入详情页，记录时间
            if (lastDetailPageTime == 0) {
                lastDetailPageTime = System.currentTimeMillis();
                logMessage("进入职位详情页面，开始计时");
            } else {
                // 检查是否超时
                long currentTime = System.currentTimeMillis();
                // 添加剩余时间提示
                long remainingTime = DETAIL_PAGE_TIMEOUT - (currentTime - lastDetailPageTime);
                if (remainingTime > 0) {
                    logMessage("职位详情页面停留时间还剩：" + (remainingTime / 1000) + "秒");
                }
                if (currentTime - lastDetailPageTime > DETAIL_PAGE_TIMEOUT) {
                    logMessage("在职位详情页停留超过5秒，执行返回操作");
                    lastDetailPageTime = 0; // 重置计时器
                    performBackOperation();
                }
            }
        } else {
            // 如果离开了职位详情页，重置计时器
            if (lastDetailPageTime != 0) {
                logMessage("离开职位详情页，重置计时器");
                lastDetailPageTime = 0;
            }
        }
    }

    // 修改通过文字列表判断页面类型的方法
    private PageType detectPageTypeByTexts(List<String> pageTexts) {
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode == null) return null;

        try {
            // 检查是否在职位列表主界面
            List<AccessibilityNodeInfo> tabNodes = rootNode.findAccessibilityNodeInfosByViewId("com.hpbr.bosszhipin:id/tv_tab_1");
            for (AccessibilityNodeInfo node : tabNodes) {
                if (node.getText() != null && 
                    node.getText().toString().equals("职位") && 
                    node.isSelected()) {
                    logMessage("检测到职位标签被选中，判断为职位主界面");
                    return PageType.MAIN_LIST;
                }
            }

            // 检查是否在职位详情页
            List<AccessibilityNodeInfo> chatButtons = rootNode.findAccessibilityNodeInfosByViewId("com.hpbr.bosszhipin:id/btn_chat");
            for (AccessibilityNodeInfo button : chatButtons) {
                if (button.getText() != null) {
                    String buttonText = button.getText().toString();
                    if (buttonText.equals("立即沟通") || buttonText.equals("继续沟通")) {
                        logMessage("检测到" + buttonText + "按钮，判断为职位详情页");
                        return PageType.JOB_DETAIL;
                    }
                }
            }

            // 检查是否在聊天页面
            List<AccessibilityNodeInfo> chatFeatures = rootNode.findAccessibilityNodeInfosByViewId("com.hpbr.bosszhipin:id/mTextView");
            for (AccessibilityNodeInfo feature : chatFeatures) {
                if (feature.getText() != null) {
                    String featureText = feature.getText().toString();
                    if (featureText.equals("发简历") || 
                        featureText.equals("换电话") || 
                        featureText.equals("换微信") || 
                        featureText.equals("不感兴趣")) {
                        logMessage("检测到" + featureText + "，判断为聊天页面");
                        return PageType.CHAT_PAGE;
                    }
                }
            }

            logMessage("未匹配到任何页面特征");
            return null;
        } finally {
            rootNode.recycle();
        }
    }

    // 添加获取页面所有文字的方法
    private List<String> getAllTextsFromPage(AccessibilityNodeInfo node) {
        List<String> texts = new ArrayList<>();
        if (node == null) return texts;
        
        // 获取当前节点的文字
        if (node.getText() != null) {
            String text = node.getText().toString().trim();
            if (!text.isEmpty()) {
                texts.add(text);
            }
        }
        
        // 递归获取所有子节点的文字
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                texts.addAll(getAllTextsFromPage(child));
                child.recycle();
            }
        }
        
        return texts;
    }

    // 添加获取节点文本的方法
    private String getTextFromNode(AccessibilityNodeInfo node) {
        if (node == null) return "";
        
        StringBuilder textBuilder = new StringBuilder();
        
        // 获取当前节点的文本
        if (node.getText() != null) {
            textBuilder.append(node.getText().toString());
        }
        
        // 递归获取所有子节点的文本
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                String childText = getTextFromNode(child);
                if (!childText.isEmpty()) {
                    if (textBuilder.length() > 0) {
                        textBuilder.append(" ");
                    }
                    textBuilder.append(childText);
                }
                child.recycle();
            }
        }
        
        return textBuilder.toString();
    }





    // 处理职位节点 - 只匹配职位名称
    private void processJobNodes(List<AccessibilityNodeInfo> jobNodes) {
        if (jobNodes.isEmpty()) {
            logMessage("未找到职位卡片，将在下次检查时滑动");
            return;
        }

        boolean foundMatchingJob = false;

        // 检查每个职位是否匹配关键词
        for (AccessibilityNodeInfo jobNode : jobNodes) {
            if (jobNode == null) continue;

            // 查找职位名称节点 - 使用特定资源ID
            List<AccessibilityNodeInfo> titleNodes = jobNode.findAccessibilityNodeInfosByViewId("com.hpbr.bosszhipin:id/tv_position_name");
            if (titleNodes.isEmpty() || titleNodes.get(0) == null || titleNodes.get(0).getText() == null) {
                continue; // 跳过没有职位名称的卡片
            }

            // 只获取职位名称文本进行匹配
            String jobTitle = titleNodes.get(0).getText().toString();
            logMessage("检查职位名称: " + jobTitle);

            // 使用职位名称与关键词匹配
            boolean containsKeyword = containsKeywords(jobTitle, keywords);

            if (containsKeyword) {
                foundMatchingJob = true;
                logMessage("✓ 匹配成功！在职位名称中找到关键词: " + getMatchedKeywords(jobTitle, keywords));

                // 生成唯一ID，避免重复点击相同职位
                String uniqueId = jobTitle;
                List<AccessibilityNodeInfo> companyNodes = jobNode.findAccessibilityNodeInfosByViewId("com.hpbr.bosszhipin:id/tv_company_name");
                if (!companyNodes.isEmpty() && companyNodes.get(0) != null && companyNodes.get(0).getText() != null) {
                    uniqueId += "_" + companyNodes.get(0).getText().toString();
                }

                if (!clickedNodes.contains(uniqueId)) {
                    // 点击匹配到的职位
                    clickedNodes.add(uniqueId);
                    logMessage("点击职位: " + jobTitle);
                    clickNode(jobNode);

                    currentState = State.VIEWING_DETAIL;
                    totalCount++;
                    logMessage("当前已处理职位数: " + totalCount + "/" + maxCount);

                    if (totalCount >= maxCount) {
                        logMessage("已达到最大处理数量，停止服务");
                        stopService();
                    }
                    return;
                } else {
                    logMessage("该职位已点击过，跳过");
                }
            } else {
                logMessage("✗ 未在职位名称中匹配到关键词，跳过");
            }
        }

        if (!foundMatchingJob) {
            logMessage("在当前页面未找到匹配的职位，将在下次检查时滑动");
        }
    }

    // 获取职位卡片中的公司名称，用于生成唯一ID
    private String getCompanyFromJobNode(AccessibilityNodeInfo jobNode) {
        if (jobNode == null) return "";

        List<AccessibilityNodeInfo> companyNodes = jobNode.findAccessibilityNodeInfosByViewId("com.hpbr.bosszhipin:id/tv_company_name");
        if (!companyNodes.isEmpty() && companyNodes.get(0) != null && companyNodes.get(0).getText() != null) {
            return companyNodes.get(0).getText().toString();
        }

        return "";
    }

    // 修改关键词匹配方法，提高精确度
    private boolean containsKeywords(String text, List<String> keywords) {
        if (text == null || text.isEmpty() || keywords.isEmpty()) {
            return false;
        }

        // 转换为小写进行比较，提高匹配准确性
        String lowerText = text.toLowerCase();

        for (String keyword : keywords) {
            if (keyword.isEmpty()) continue;

            // 使用小写比较避免大小写问题
            if (lowerText.contains(keyword.toLowerCase())) {
                return true;
            }
        }

        return false;
    }

    // 获取匹配到的关键词列表，用于日志
    private String getMatchedKeywords(String text, List<String> keywords) {
        if (text == null || text.isEmpty() || keywords.isEmpty()) {
            return "[]";
        }

        List<String> matched = new ArrayList<>();
        String lowerText = text.toLowerCase();

        for (String keyword : keywords) {
            if (keyword.isEmpty()) continue;

            if (lowerText.contains(keyword.toLowerCase())) {
                matched.add(keyword);
            }
        }

        return matched.toString();
    }

    // 添加用于跟踪职位列表的变量
    private String lastFirstJobText = null;
    private int stuckCount = 0;

    private long lastAutoScrollTime = 0;
    private static final long AUTO_SCROLL_INTERVAL = 3000; // 3秒自动滑动一次

    // 处理在主界面时的操作
    private void processMainPage(AccessibilityNodeInfo rootNode) {
        // 首先尝试滑动，解决可能的卡顿
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastAutoScrollTime >= AUTO_SCROLL_INTERVAL) {
            logMessage("强制执行定时自动滑动");
            performScrollDown();  // 使用一个不同的名称调用滑动方法
            lastAutoScrollTime = currentTime;
            // 短暂延迟后再处理职位
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {}
        }

        // 然后处理职位信息
        // ... 现有代码 ...
    }

    // 向下滑动方法 - 增加滑动幅度
    private void performScrollDown() {
        logMessage("开始执行滑动操作 - 强制滑动模式");
        
        try {
            // 获取屏幕尺寸
            DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
            int screenHeight = displayMetrics.heightPixels;
            int screenWidth = displayMetrics.widthPixels;
            
            // 创建一个更大幅度的滑动路径
            Path path = new Path();
            path.moveTo(screenWidth / 2, (int)(screenHeight * 0.9f));  // 从屏幕90%位置开始
            path.lineTo(screenWidth / 2, (int)(screenHeight * 0.1f));  // 滑动到屏幕10%位置
            
            // 创建手势描述，使用适当的持续时间以确保滑动效果
            GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
            GestureDescription.StrokeDescription stroke = new GestureDescription.StrokeDescription(path, 0, 400);
            gestureBuilder.addStroke(stroke);
            
            // 执行手势
            boolean result = dispatchGesture(gestureBuilder.build(), null, null);
            logMessage("滑动手势执行状态: " + (result ? "成功" : "失败") + ", 幅度: 90%->10%");
            
            // 强制更新滑动时间戳
            lastAutoScrollTime = System.currentTimeMillis();
        } catch (Exception e) {
            logMessage("滑动时发生异常: " + e.getMessage());
        }
    }

    // 处理职位详情页面 - 修复点击职位后立即返回的问题
    private void handleJobDetailPage(AccessibilityNodeInfo rootNode) {
        if (rootNode == null) return;
        
        // 添加足够的等待时间，确保页面完全加载
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastStateChangeTime < 3000) {
            logMessage("职位详情页面刚刚加载，等待界面稳定...");
            return;  // 等待页面完全加载
        }
        
        // 查找立即沟通或继续沟通按钮
        List<AccessibilityNodeInfo> chatButtons = rootNode.findAccessibilityNodeInfosByViewId("com.hpbr.bosszhipin:id/btn_chat");
        if (!chatButtons.isEmpty()) {
            AccessibilityNodeInfo chatButton = chatButtons.get(0);
            if (chatButton != null && chatButton.isClickable()) {
                String buttonText = chatButton.getText() != null ? chatButton.getText().toString() : "";
                
                // 根据按钮文本决定操作
                if ("立即沟通".equals(buttonText)) {
                    logMessage("找到立即沟通按钮，执行点击");
                    clickNode(chatButton);
                    lastStateChangeTime = currentTime;  // 更新状态变化时间
                    currentState = State.COMMUNICATING;
                    greetingCount++;
                    return;
                } 
                else if ("继续沟通".equals(buttonText)) {
                    logMessage("找到继续沟通按钮，等待2秒后返回");
                    handler.postDelayed(() -> {
                        performGlobalAction(GLOBAL_ACTION_BACK);
                        logMessage("执行返回操作");
                    }, 2000);
                    return;
                }
            }
            
            if (chatButton != null) {
                chatButton.recycle();
            }
        }
        
        // 添加最大等待时间，如果超过10秒仍未找到沟通按钮，则执行返回
        if (currentTime - lastStateChangeTime > 10000) {
            logMessage("职位详情页面等待超时(10秒)，未找到沟通按钮，执行返回");
            performGlobalAction(GLOBAL_ACTION_BACK);
            lastStateChangeTime = currentTime;  // 更新状态变化时间
            return;
        }
        
        logMessage("职位详情页面未找到沟通按钮，继续等待...");
    }

    // 主要处理方法 - 确保在正确的地方调用handleJobDetailPage
    private void processAccessibilityEvent() {
        // 获取当前界面根节点
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode == null) {
            logMessage("无法获取界面信息，跳过处理");
            return;
        }
        
        try {
            // 获取当前页面类型
            PageType currentPage = getCurrentPageType(rootNode);
            logMessage("【界面检测】当前所在页面：" + getPageTypeString(currentPage));
            
            // 根据不同页面类型执行不同操作
            if (currentPage == PageType.MAIN_LIST) {
                // 在职位列表页面，处理列表或滑动
                handleMainList(rootNode);
            } else if (currentPage == PageType.JOB_DETAIL) {
                // 在职位详情页面，处理沟通按钮
                handleJobDetailPage(rootNode);
                return; // 重要：处理完详情页后直接返回，不执行其他操作
            } else if (currentPage == PageType.CHAT_PAGE) {
                // 在聊天页面，处理沟通
                handleChatPage(rootNode);
                return; // 重要：处理完聊天页后直接返回，不执行其他操作
            } else {
                // 未知页面，尝试返回主界面，除非我们刚从主界面过来
                if (previousPageType == PageType.MAIN_LIST) {
                    logMessage("从主界面进入未知页面，可能是弹窗，尝试继续操作");
                    // 延迟后再次检查
                    handler.postDelayed(this::processAccessibilityEvent, 1000);
                } else {
                    logMessage("在未知界面，尝试返回");
                    performBackOperation();
                }
            }
        } finally {
            rootNode.recycle();
        }
    }
    
    // 添加帮助方法，获取页面类型的描述字符串
    private String getPageTypeString(PageType pageType) {
        if (pageType == null) return "未知页面";
        switch (pageType) {
            case MAIN_LIST: return "职位列表主页面";
            case JOB_DETAIL: return "职位详情页面";
            case CHAT_PAGE: return "聊天页面";
            default: return "未定义页面";
        }
    }

} // 类结束 