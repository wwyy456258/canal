package com.alibaba.otter.canal.filter.aviater;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;

import com.alibaba.otter.canal.filter.CanalEventFilter;
import com.alibaba.otter.canal.filter.exception.CanalFilterException;
import com.googlecode.aviator.AviatorEvaluator;
import com.googlecode.aviator.Expression;
import org.springframework.util.CollectionUtils;

/**
 * 基于aviater进行tableName正则匹配的过滤算法
 *
 * @author jianghang 2012-7-20 下午06:01:34
 */
public class AviaterRegexFilter implements CanalEventFilter<String> {

    private static final String SPLIT = ",";
    private static final String PATTERN_SPLIT = "|";
    private static final String FILTER_EXPRESSION = "regex(pattern,target)";
    private static final RegexFunction regexFunction = new RegexFunction();
    private final Expression exp = AviatorEvaluator.compile(FILTER_EXPRESSION, true);

    static {
        AviatorEvaluator.addFunction(regexFunction);
    }

    private static final Comparator<String> COMPARATOR = new StringComparator();

    final private List<String> patterns;
    final private boolean defaultEmptyValue;

    private static final int patternMaxLength = 40000;

    public AviaterRegexFilter(String pattern) {
        this(pattern, true);
    }

    public AviaterRegexFilter(String pattern, boolean defaultEmptyValue) {
        this.defaultEmptyValue = defaultEmptyValue;
        List<String> list = null;
        if (StringUtils.isEmpty(pattern)) {
            list = new ArrayList<String>();
        } else {
            String[] ss = StringUtils.split(pattern, SPLIT);
            list = Arrays.asList(ss);
        }

        // 对pattern按照从长到短的排序
        // 因为 foo|foot 匹配 foot 会出错，原因是 foot 匹配了 foo 之后，会返回 foo，但是 foo 的长度和 foot
        // 的长度不一样
        Collections.sort(list, COMPARATOR);
        // 对pattern进行头尾完全匹配
        list = completionPattern(list);
        List<String> patterns = new ArrayList<String>();
        splitPattern(list, patterns);
        this.patterns = patterns;
    }

    private static void splitPattern(List<String> head, List<String> patterns) {
        if(CollectionUtils.isEmpty(head)){
            return;
        }
        String resp = null;
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < head.size(); i++) {
            resp = stringBuilder.toString();
            stringBuilder.append(head.get(i)).append(PATTERN_SPLIT);
            if(stringBuilder.length() > patternMaxLength){
                patterns.add(resp.substring(0, resp.length() -1));
                splitPattern(head.subList(i, head.size()), patterns);
                return;
            }
        }
        patterns.add(stringBuilder.substring(0, stringBuilder.length() -1));
    }

    public boolean filter(String filtered) throws CanalFilterException {
        if (CollectionUtils.isEmpty(patterns)) {
            return defaultEmptyValue;
        }

        if (StringUtils.isEmpty(filtered)) {
            return defaultEmptyValue;
        }
        for (String pattern : patterns) {
            Map<String, Object> env = new HashMap<String, Object>();
            env.put("pattern", pattern);
            env.put("target", filtered.toLowerCase());
            Boolean resp = (Boolean) exp.execute(env);
            if(resp){
                return resp;
            }
        }
        return false;
    }

    /**
     * 修复正则表达式匹配的问题，因为使用了 oro 的 matches，会出现：
     *
     * <pre>
     * foo|foot 匹配 foot 出错，原因是 foot 匹配了 foo 之后，会返回 foo，但是 foo 的长度和 foot 的长度不一样
     * </pre>
     * <p>
     * 因此此类对正则表达式进行了从长到短的排序
     *
     * @author zebin.xuzb 2012-10-22 下午2:02:26
     * @version 1.0.0
     */
    private static class StringComparator implements Comparator<String> {

        @Override
        public int compare(String str1, String str2) {
            if (str1.length() > str2.length()) {
                return -1;
            } else if (str1.length() < str2.length()) {
                return 1;
            } else {
                return 0;
            }
        }
    }

    /**
     * 修复正则表达式匹配的问题，即使按照长度递减排序，还是会出现以下问题：
     *
     * <pre>
     * foooo|f.*t 匹配 fooooot 出错，原因是 fooooot 匹配了 foooo 之后，会将 fooo 和数据进行匹配，但是 foooo 的长度和 fooooot 的长度不一样
     * </pre>
     * <p>
     * 因此此类对正则表达式进行头尾完全匹配
     *
     * @author simon
     * @version 1.0.0
     */

    private List<String> completionPattern(List<String> patterns) {
        List<String> result = new ArrayList<String>();
        for (String pattern : patterns) {
            StringBuffer stringBuffer = new StringBuffer();
            stringBuffer.append("^");
            stringBuffer.append(pattern);
            stringBuffer.append("$");
            result.add(stringBuffer.toString());
        }
        return result;
    }

    @Override
    public String toString() {
        return patterns.toString();
    }

}
