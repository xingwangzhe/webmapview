(function() {
    'use strict';

    // 脚本加载日志
    if (window.java && typeof window.java.log === 'function') {
        window.java.log('[虚拟玩家] injector.js 脚本加载中...');
    }

    // 添加与 Java 的交互
    if (window.java && typeof window.java.log === 'function') {
        window.java.log('[虚拟玩家] 注入脚本开始执行');
    } else {
        console.warn('[虚拟玩家] window.java 或 log 方法不存在');
    }

    // 修改日志输出到 Java
    function logToJava(message) {
        if (window.java && typeof window.java.log === 'function') {
            window.java.log(message);
        }
    }

    logToJava('[虚拟玩家] 注入脚本开始执行');

    let originalFetch = window.fetch;
    const localJsonPath = 'run/players.json'; // 本地生成的JSON文件路径

    // 拦截fetch请求
    window.fetch = async function(...args) {
        const [resource, config] = args;

        try {
            logToJava(`[虚拟玩家] 发起 fetch 请求: ${resource}`);
            const response = await originalFetch.apply(this, args);

            // 检查是否是玩家数据请求
            if (typeof resource === 'string' && resource.includes('tiles/players.json')) {
                logToJava(`[虚拟玩家] 🎯 拦截到玩家数据请求: ${resource}`);

                // 克隆响应以便修改
                const responseClone = response.clone();
                const originalData = await responseClone.json();

                logToJava('[虚拟玩家] 📥 原始玩家数据: ' + JSON.stringify(originalData));

                // 读取本地生成的JSON文件
                let localData = {};
                try {
                    logToJava(`[虚拟玩家] 尝试读取本地 JSON 文件: ${localJsonPath}`);
                    const localResponse = await fetch(localJsonPath);
                    localData = await localResponse.json();
                    logToJava('[虚拟玩家] 📥 本地玩家数据: ' + JSON.stringify(localData));
                } catch (e) {
                    logToJava('[虚拟玩家] ❌ 无法读取本地JSON文件: ' + e);
                }

                // 合并本地数据和原始数据
                const modifiedData = mergePlayerData(originalData, localData);

                // 创建新的响应
                const modifiedResponse = new Response(JSON.stringify(modifiedData), {
                    status: response.status,
                    statusText: response.statusText,
                    headers: response.headers
                });

                logToJava('[虚拟玩家] 📤 已修改玩家数据 - 虚拟玩家已注入');
                return modifiedResponse;
            }

            logToJava(`[虚拟玩家] 未拦截的 fetch 请求: ${resource}`);
            return response;
        } catch (error) {
            logToJava(`[虚拟玩家] ❌ fetch 请求失败: ${error.message}`);
            logToJava(`[虚拟玩家] ❌ 请求详细信息: ${JSON.stringify({ resource, config })}`);
            throw error;
        }
    };

    // 合并玩家数据
    function mergePlayerData(originalData, localData) {
        const mergedData = JSON.parse(JSON.stringify(originalData));

        if (localData.players && Array.isArray(localData.players)) {
            mergedData.players = mergedData.players || [];
            mergedData.players.push(...localData.players);
        }

        mergedData.max = Math.max(originalData.max || 0, localData.max || 0);
        return mergedData;
    }

    // 锁定window.fetch，防止被后续脚本覆盖
    try {
        Object.defineProperty(window, 'fetch', {
            configurable: false,
            enumerable: true,
            writable: false,
            value: window.fetch
        });
        logToJava('[虚拟玩家] window.fetch 已锁定，防止被覆盖');
    } catch (e) {
        logToJava('[虚拟玩家] window.fetch 锁定失败: ' + e);
    }

    logToJava('[虚拟玩家] 注入脚本执行完成');
})();
