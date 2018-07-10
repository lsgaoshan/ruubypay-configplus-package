package com.ruubypay.framework.configx;

import com.ruubypay.framework.configx.observer.IObserver;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 配置组抽象实现。
 *
 * @author chenhaiyang
 */
@Slf4j
public abstract class AbstractGeneralConfigGroup extends ConcurrentHashMap<String, String> implements ConfigGroup {

    /**
     * 判断配置是否需要解密
     */
    private static final String NEED_DECRYPT = "{cipher}";

    /**
     * 加密接口
     */
    private Encrypt encrypt;

    protected AbstractGeneralConfigGroup() {
    }

    protected AbstractGeneralConfigGroup(Encrypt encrypt) {
        this.encrypt = encrypt;
    }

    /**
     * 根据key获取配置。处理加解密逻辑
     *
     * @param key key
     * @return 返回配置
     */
    @Override
    public final String get(String key) {
        String value = super.get(key);
        if (value != null && value.startsWith(NEED_DECRYPT) && encrypt != null) {
            value = value.substring(NEED_DECRYPT.length(), value.length());
            try {
                value = encrypt.decrypt(value);
            } catch (Exception e) {
                log.error("decrtpt key:{} value:{},err", key, value, e);
            }
        }
        return value;
    }

    /**
     * 重载函数，根据object类型的key获取配置
     *
     * @param key jey
     * @return 返回配置
     */
    @Override
    public final String get(Object key) {
        return get(key.toString());
    }

    /**
     * 根据key删除配置
     *
     * @param key key
     */
    @Override
    public String remove(Object key) {
        throw new UnsupportedOperationException();
    }

    /**
     * clear整个配置
     */
    @Override
    public void clear() {
        throw new UnsupportedOperationException();
    }

    /**
     * 向本地配置组添加配置
     *
     * @param key   key
     * @param value value
     * @return 返回上一次的配置值
     */
    @Override
    public final String put(String key, String value) {

        value = Objects.requireNonNull(value).trim();
        String preValue = super.get(key);

        if (!Objects.equals(preValue, value)) {
            log.debug("Key {} change from {} to {}", key, preValue, value);
            super.put(key, value);
            //如果值变了，通知观察者
            if (preValue != null) {
                notify(key, value);
            }
        }
        return preValue;
    }


    /**
     * 清除本地配置，重新载入最新的配置
     *
     * @param configs 最新的配置集
     */
    protected final void cleanAndPutAll(Map<String, String> configs) {

        if (configs == null || configs.size() == 0) {
            log.debug("bean group has none keys, clear.");
            super.clear();
            return;
        }
        //config中不包含，但本地包含的key，需要清除掉
        if (this.size() > 0) {
            final Set<String> newKeys = configs.keySet();
            this.keySet()
                    .stream()
                    .filter(input -> !newKeys.contains(input))
                    .forEach(super::remove);
        }
        //重新将新的配置 写入缓存
        configs.forEach(this::put);
    }


    /**
     * 观察者列表
     */
    private final List<IObserver> watchers = new ArrayList<>();

    /**
     * 注册成为一个观察者，当配置变更后收到通知
     *
     * @param watcher 观察者
     */
    @Override
    public void register(final IObserver watcher) {
        watchers.add(Objects.requireNonNull(watcher));
    }

    /**
     * 通知所有的观察者，配置已经变更
     *
     * @param key   属性key
     * @param value 属性value
     */
    @Override
    public void notify(final String key, final String value) {
        watchers.forEach(watcher ->
                new Thread(() -> watcher.notifyObserver(key, value)).start()
        );
    }

    /**
     * 通知所有的观察者，配置已经变更，需要重新reload所有配置
     */
    @Override
    public void notifyAllKey() {
        watchers.forEach(watcher ->
                new Thread(watcher::notifyObserver).start()
        );
    }
}
