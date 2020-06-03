# Plugin-Mybatis

### Plugin-Mybatis 能做什么？

> 基于Plugin-Mybatis实现以下功能
- 辅助分页(为什么不能更傻瓜式,因为你会碰到无法想象的关联以及一些子查询的时候,你会发现插件真的就是傻瓜。)
- 辅助枚举的装换
- 辅助乐观锁（只支持时间方式）

---
### 使用环境

    JAVA    >  1.8
    Maven   >  3.X
    Mybatis >  3.X
    
### Maven仓库
    
```xml

<dependency>
  <groupId>com.github.link-kou</groupId>
  <artifactId>mybatis</artifactId>
  <version>1.0.2</version>
</dependency>

```

## 我为什么不用MyBatis-Plus聊一下我的观点

> 要绝对肯定mybatis-plus的优秀之处

-  [MyBatis-Plus](https://github.com/baomidou/mybatis-plus)很牛的开源项目。

> 我的原因

-  以辅助为目的,相对减少代码量。
-  mybatis已经很强大了。几乎用不到mybatis-plus的任何功能。
-  XML一定要写，而且是必须写。这个是强制的，没有任何人可以对此条发起挑战。很简单的一个原因解耦和维护
-  我严重反对代码式的SQL查询。方便是方便。维护真的是坑上加坑。开发一时爽,一直开发一直爽，维护火葬场。

> 我反对代码式的Sql查询，严重拒绝

```text

我在工作的早年，经常性碰到resultMap到底写不写的问题。多数情况下都直接上实体，因为方便和感觉resultMap作用性不大。
时间久了，项目经验多了。发现你不可能要求个个开发都是无敌存在。
1: 你会发现多张表都有一个Name字段。在LeftJoin的时候,你的实体默默多创建了一个。
2: 数据库实体被滥用，导致多个地方使用。想改都改不动。人生都已经没有乐趣了
3: 数据库字段不能直接返回输出到前端去,一个都不行。

```
### 示列

```xml
<!-- myBatis文件 -->
<bean id="sqlSessionFactory" class="org.mybatis.spring.SqlSessionFactoryBean">
    <property name="dataSource" ref="dataSource"/>
    <property name="mapperLocations" value="classpath*:com/mybatis/test/**/mapper/**/*Mapper.xml"/>
    <property name="typeHandlers">
        <array>
            <bean class="com.linkkou.mybatis.mybatisplugins.TypeHandlers.AutoGsonEnumTypeHandler"/>
        </array>
    </property>
    <property name="plugins">
        <array>
            <bean class="com.linkkou.mybatis.mybatisplugins.QueryPaginatorInterceptor">
                <property name="paginatorType" value="true"/>
            </bean>
        </array>
    </property>
</bean>
```
