/*
 *  Copyright (c) 2022-2023, Mybatis-Flex (fuhai999@gmail.com).
 *  <p>
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *  <p>
 *  http://www.apache.org/licenses/LICENSE-2.0
 *  <p>
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.mybatisflex.test;

import com.mybatisflex.core.FlexGlobalConfig;
import com.mybatisflex.core.MybatisFlexBootstrap;
import com.mybatisflex.core.audit.AuditManager;
import com.mybatisflex.core.audit.ConsoleMessageCollector;
import com.mybatisflex.core.audit.MessageCollector;
import com.mybatisflex.core.keygen.KeyGeneratorFactory;
import com.mybatisflex.core.mybatis.Mappers;
import com.mybatisflex.core.query.If;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.row.DbChain;
import com.mybatisflex.core.update.UpdateChain;
import com.mybatisflex.core.update.UpdateWrapper;
import com.mybatisflex.core.util.UpdateEntity;
import com.mybatisflex.mapper.Account6Mapper;
import com.mybatisflex.mapper.Account7Mapper;
import com.mybatisflex.mapper.ArticleMapper;
import org.apache.ibatis.logging.stdout.StdOutImpl;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import javax.sql.DataSource;
import java.util.List;

import static com.mybatisflex.test.table.AccountTableDef.ACCOUNT;
import static com.mybatisflex.test.table.ArticleTableDef.ARTICLE;


public class AccountTester {

    static AccountMapper accountMapper;
    static ArticleMapper articleMapper;

    @BeforeClass
    public static void init() {
        DataSource dataSource = new EmbeddedDatabaseBuilder()
            .setType(EmbeddedDatabaseType.H2)
            .addScript("schema.sql")
            .addScript("data.sql")
            .build();

        FlexGlobalConfig.getDefaultConfig()
            .setLogicDeleteColumn("is_delete");

        KeyGeneratorFactory.register("test", new TestKeyGenerator());

        MybatisFlexBootstrap bootstrap = MybatisFlexBootstrap.getInstance()
            .setDataSource(dataSource)
            .setLogImpl(StdOutImpl.class)
            .addMapper(AccountMapper.class)
            .addMapper(Account6Mapper.class)
            .addMapper(Account7Mapper.class)
            .addMapper(ArticleMapper.class)
            .start();

        //开启审计功能
        AuditManager.setAuditEnable(true);

        //设置 SQL 审计收集器
        MessageCollector collector = new ConsoleMessageCollector();
        AuditManager.setMessageCollector(collector);


        accountMapper = bootstrap.getMapper(AccountMapper.class);
        articleMapper = bootstrap.getMapper(ArticleMapper.class);
    }

    @Test
    public void testLogicDelete() {
        accountMapper.selectAll().forEach(System.out::println);
    }

    @Test
    public void testExecutor() {
        DbChain.table("tb_account")
            .select(ACCOUNT.ALL_COLUMNS)
            .from(ACCOUNT)
            .where(ACCOUNT.ID.ge(1))
            .listAs(Account.class)
            .forEach(System.out::println);

        AccountMapper accountBaseMapper = (AccountMapper) Mappers.ofEntityClass(Account.class);

        AccountMapper accountMapper = Mappers.ofMapperClass(AccountMapper.class);
        System.out.println(">>>>> : " + (accountBaseMapper == accountMapper));

        Account account = accountBaseMapper.selectOneById(1);
        System.out.println(">>>> account: " + account);
    }

    @Test
    public void testLambda() {
        QueryWrapper queryWrapper = QueryWrapper.create();
        queryWrapper.where(Account::getId).ge(100)
            .and(Account::getUserName).like("michael")
            .or(Account::getUserName).like(null, If::notNull);
        System.out.println(queryWrapper.toSQL());
    }

    @Test
    public void testTenant() {
        QueryWrapper queryWrapper = QueryWrapper.create();
        queryWrapper.where(Account::getId).ge(1);
        List<Account> accounts = accountMapper.selectListByQuery(queryWrapper);
        System.out.println(accounts);
    }

    @Test
    public void testLeftJoinForLogicDelete() {
        QueryWrapper queryWrapper = QueryWrapper.create();
        queryWrapper.from(ARTICLE)
            .leftJoin(ACCOUNT).on(ARTICLE.ACCOUNT_ID.eq(ACCOUNT.ID))
            .where(ARTICLE.ID.ge(1));
        List<Article> accounts = articleMapper.selectListByQuery(queryWrapper);
        System.out.println(accounts);
    }

    /**
     * issues  https://gitee.com/mybatis-flex/mybatis-flex/issues/I7QD29
     */
    @Test
    public void testLeftJoinSelfForLogicDelete() {
        QueryWrapper queryWrapper = QueryWrapper.create();
        queryWrapper.from(ACCOUNT)
            .leftJoin(ACCOUNT).as("a1").on(ACCOUNT.ID.eq(ARTICLE.ACCOUNT_ID))
            .leftJoin(ACCOUNT).as("a2").on(ACCOUNT.ID.eq(ARTICLE.ACCOUNT_ID))
            .where(ACCOUNT.ID.ge(1));
        List<Article> accounts = articleMapper.selectListByQuery(queryWrapper);
        System.out.println(accounts);
    }


    /**
     * issues https://gitee.com/mybatis-flex/mybatis-flex/issues/I7VAG8
     */
    @Test
    public void testLeftJoinSelectWithIgnoreColumn() {
        QueryWrapper queryWrapper = QueryWrapper.create();
        queryWrapper
            .select(ACCOUNT.ID, ACCOUNT.AGE, ARTICLE.TITLE)
            .from(ACCOUNT)
            .leftJoin(ARTICLE).on(ACCOUNT.ID.eq(ARTICLE.ACCOUNT_ID))
            .where(ACCOUNT.ID.ge(1));
        List<Account> accounts = accountMapper.selectListByQuery(queryWrapper);
        System.out.println(accounts);
    }


    /**
     * issues https://gitee.com/mybatis-flex/mybatis-flex/issues/I7RE0J
     */
    @Test
    public void testUpdateByUpdateWrapper() {
        Account account = new Account();
        account.setId(1L);
        account = UpdateWrapper.of(account)
            .set(Account::getId, 1)
            .set(Account::getAge, 20)
            //设置 Ignore 字段，会被自动忽略
            .setRaw(Account::getTitle, "xxxx")
            .toEntity();
        accountMapper.update(account);
    }


    @Test
    public void testSelectAsToDTO() {
        QueryWrapper queryWrapper = QueryWrapper.create();
//        queryWrapper.select(ACCOUNT.ALL_COLUMNS,ARTICLE.TITLE.as(AccountDTO::getPermissions))
        queryWrapper.select(ACCOUNT.ALL_COLUMNS, ACCOUNT.USER_NAME.as(AccountDTO::getTestOtherField))
//        queryWrapper.select(ACCOUNT.ALL_COLUMNS)
            .from(ACCOUNT).leftJoin(ARTICLE).on(ACCOUNT.ID.eq(ARTICLE.ACCOUNT_ID));
        List<AccountDTO> accountDTOS = accountMapper.selectListByQueryAs(queryWrapper, AccountDTO.class);
        System.out.println(accountDTOS);
    }


    @Test
    public void testUpdate1() {
        List<Account> accounts = accountMapper.selectAll();
        System.out.println(accounts);


        Account account = UpdateEntity.of(Account.class, 1);
        account.setUserName("lisi");

        UpdateWrapper.of(account)
            .setRaw("age", "age + 1");
        accountMapper.update(account);


        accounts = accountMapper.selectAll();
        System.out.println(accounts);
    }


    @Test
    public void testUpdate2() {
        List<Account> accounts = accountMapper.selectAll();
        System.out.println(accounts);


        UpdateChain.of(Account.class)
            .set(Account::getUserName, "zhangsan123")
//            .leftJoin(ARTICLE).on(ARTICLE.ACCOUNT_ID.eq(ACCOUNT.ID))
            .where(Account::getId).eq(1)
//            .and(ARTICLE.ID.ge(0))
            .limit(1)
            .remove();

        accounts = accountMapper.selectAll();
        System.out.println(accounts);
    }

    /**
     * https://gitee.com/mybatis-flex/mybatis-flex/issues/I7L6DF
     */
    @Test
    public void testInsertSelectiveWithPk() {
        List<Account> accounts = accountMapper.selectAll();
        System.out.println(accounts);


        Account account = new Account();
        account.setId(4L);
        account.setUserName("test04");
        int rows = accountMapper.insertSelectiveWithPk(account);
        System.out.println(rows);

        accounts = accountMapper.selectAll();
        System.out.println(accounts);

    }


    @Test
    public void testInsertWithRaw() {
        Account account = new Account();
        account.setUserName("michael");

        Account newAccount = UpdateWrapper.of(account)
//            .setRaw("birthday", "now()")
//            .setRaw(ACCOUNT.BIRTHDAY, "now()")
            .setRaw(Account::getBirthday, "now()")
            .toEntity();

        accountMapper.insert(newAccount);
    }


    /**
     * issues https://gitee.com/mybatis-flex/mybatis-flex/issues/I873OZ
     */
    @Test
    public void testInsertSelective01() {
        Account6Mapper mapper = MybatisFlexBootstrap.getInstance()
            .getMapper(Account6Mapper.class);

        Account6 account1 = new Account6();
        account1.setId(1L);
        account1.setUserName("michael");
        account1.setAge(5);

        Assert.assertEquals(mapper.insertSelective(account1), 1);


        Account6 account2 = new Account6();
//        account2.setId(1L); 不设置主键
        account2.setUserName("michael");
        account2.setAge(5);

        Assert.assertEquals(mapper.insertSelective(account2), 1);
    }


    /**
     * issues https://gitee.com/mybatis-flex/mybatis-flex/issues/I88TX1
     */
    @Test
    public void testInsertWithEntityId() {
        Account7Mapper mapper = MybatisFlexBootstrap.getInstance()
            .getMapper(Account7Mapper.class);

        Account7 account1 = new Account7();
        account1.setId(1L);
        account1.setUserName("michael");
        account1.setAge(5);

        int result1 = mapper.insert(account1);
        Assert.assertEquals(result1, 1);


        Account7 account2 = new Account7();
//        account2.setId(1L); 不设置主键，自动生成主键
        account2.setUserName("michael");
        account2.setAge(5);

        int result2 = mapper.insert(account2);
        Assert.assertEquals(result2, 1);
    }


}
