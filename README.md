# Plugin-Mybatis

### Plugin-Mybatis 能做什么？

> 基于Plugin-Mybatis实现以下功能
- 辅助分页
- Data、Time、Timestamp需要为NUll的情况或0000-00-00 00:00:00
- 辅助枚举的装换
- 多租户和读写分离（未开发）

---
### 使用环境

    JAVA    >  1.8
    Maven   >  3.X
    Mybatis >  3.X
    
### Maven仓库
    
```xml：

<dependency>
  <groupId>com.github.link-kou</groupId>
  <artifactId>mybatis</artifactId>
  <version>1.0.0</version>
</dependency>

```

## 几个点

> 与MyBatis-Plus比较

-  MyBatis-Plus很牛的开源项目。我的目的不是替代或与其争锋。而且我觉得MyBatis-Plus做的过度了。

> 我目的

-  以辅助为目的,相对减少代码量。


> 我反对代码式的Sql查询，严重拒绝

-  不利于维护，开发一时爽,一直开发一直爽，维护火葬场。
```xml:
我在工作的早年，经常性碰到resultMap到底写不写的问题。多数情况下都直接上实体，因为方便和感觉最终resultMap作用性不大。
时间久了，项目经验多了。发现你不可能要求个个开发都是无敌存在。
导致实体污染接口、还有别名的使用、历史悠久性的接口修改实体。
这个时候才发现resultMap起到无与伦比的优势
```
### 示列

> 无
