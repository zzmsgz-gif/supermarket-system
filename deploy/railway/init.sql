-- MySQL dump 10.13  Distrib 8.0.30, for Win64 (x86_64)
--
-- Host: localhost    Database: supermarket_system
-- ------------------------------------------------------
-- Server version	8.0.30

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `activity`
--

DROP TABLE IF EXISTS `activity`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `activity` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `name` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Activity name',
  `type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'FULL_REDUCTION | DISCOUNT',
  `scope` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'ALL | CATEGORY | PRODUCT',
  `category_id` bigint unsigned DEFAULT NULL COMMENT 'Scope=CATEGORY',
  `product_id` bigint unsigned DEFAULT NULL COMMENT 'Scope=PRODUCT',
  `threshold` decimal(10,2) DEFAULT NULL COMMENT 'FULL_REDUCTION threshold',
  `discount` decimal(10,2) DEFAULT NULL COMMENT 'FULL_REDUCTION amount | DISCOUNT rate(0.9=9折)',
  `start_time` datetime NOT NULL COMMENT 'Active from',
  `end_time` datetime NOT NULL COMMENT 'Active to',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '1 enabled, 0 disabled',
  `priority` int NOT NULL DEFAULT '0' COMMENT 'Higher = matched first on tie',
  `deleted` tinyint NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_activity_scope` (`scope`),
  KEY `idx_activity_status_time` (`status`,`start_time`,`end_time`)
) ENGINE=InnoDB AUTO_INCREMENT=28 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='营销活动';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `activity`
--

LOCK TABLES `activity` WRITE;
/*!40000 ALTER TABLE `activity` DISABLE KEYS */;
INSERT INTO `activity` VALUES (26,'全场满200减50','FULL_REDUCTION','ALL',NULL,NULL,200.00,50.00,'2026-09-01 00:00:00','2026-12-31 23:59:59',1,0,0,'2026-09-01 21:59:47','2026-09-03 20:51:15'),(27,'全场满200打8折','DISCOUNT','ALL',NULL,NULL,200.00,0.80,'2026-09-03 00:00:00','2026-09-07 00:00:00',1,0,0,'2026-09-03 20:56:33','2026-09-03 20:56:33');
/*!40000 ALTER TABLE `activity` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `cart_item`
--

DROP TABLE IF EXISTS `cart_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `cart_item` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `user_id` bigint unsigned NOT NULL COMMENT 'Owner user id',
  `product_id` bigint unsigned NOT NULL COMMENT 'Product id',
  `quantity` int NOT NULL DEFAULT '1' COMMENT 'Quantity',
  `selected` tinyint NOT NULL DEFAULT '1' COMMENT '1 selected, 0 not selected',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `sku_spec` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT 'Selected SKU spec snapshot (empty = no spec)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cart_user_product` (`user_id`,`product_id`,`sku_spec`),
  KEY `idx_cart_user_selected` (`user_id`,`selected`),
  KEY `fk_cart_product` (`product_id`),
  CONSTRAINT `fk_cart_product` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`),
  CONSTRAINT `fk_cart_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`),
  CONSTRAINT `chk_cart_quantity` CHECK ((`quantity` > 0)),
  CONSTRAINT `chk_cart_selected` CHECK ((`selected` in (0,1)))
) ENGINE=InnoDB AUTO_INCREMENT=105 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Shopping cart items';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `cart_item`
--

LOCK TABLES `cart_item` WRITE;
/*!40000 ALTER TABLE `cart_item` DISABLE KEYS */;
INSERT INTO `cart_item` VALUES (2,5,1,2,1,'2026-08-02 16:41:45','2026-08-30 18:52:06',''),(37,36,9,2,1,'2026-08-29 19:19:17','2026-08-30 18:52:06',''),(104,21,88,1,1,'2026-09-03 22:03:07','2026-09-03 22:03:07','');
/*!40000 ALTER TABLE `cart_item` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `coupon`
--

DROP TABLE IF EXISTS `coupon`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `coupon` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Display name',
  `threshold_amount` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT 'Min order total to use',
  `discount_amount` decimal(10,2) NOT NULL COMMENT 'Discount value',
  `total_count` int NOT NULL DEFAULT '0' COMMENT 'Total issuable count, 0 = unlimited',
  `received_count` int NOT NULL DEFAULT '0' COMMENT 'Already received count',
  `start_time` datetime NOT NULL COMMENT 'Valid from',
  `end_time` datetime NOT NULL COMMENT 'Valid until',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '1 enabled, 0 disabled',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_coupon_status_time` (`status`,`start_time`,`end_time`),
  CONSTRAINT `chk_coupon_amounts` CHECK (((`threshold_amount` >= 0) and (`discount_amount` > 0) and (`discount_amount` <= `threshold_amount`))),
  CONSTRAINT `chk_coupon_counts` CHECK (((`total_count` >= 0) and (`received_count` >= 0))),
  CONSTRAINT `chk_coupon_deleted` CHECK ((`deleted` in (0,1))),
  CONSTRAINT `chk_coupon_status` CHECK ((`status` in (0,1)))
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Coupons';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `coupon`
--

LOCK TABLES `coupon` WRITE;
/*!40000 ALTER TABLE `coupon` DISABLE KEYS */;
INSERT INTO `coupon` VALUES (1,'新用户满 50 减 10',50.00,10.00,100,3,'2026-08-28 23:30:31','2026-11-26 23:30:31',1,'2026-08-28 23:30:31','2026-08-29 16:47:24',0),(2,'超市大促满 100 减 25',100.00,25.00,100,5,'2026-08-28 23:30:31','2026-09-27 23:30:31',1,'2026-08-28 23:30:31','2026-09-03 21:00:24',0),(3,'日常满 30 减 5',30.00,5.00,0,3,'2026-08-28 23:30:31','2027-02-24 23:30:31',1,'2026-08-28 23:30:31','2026-08-29 16:47:24',0),(5,'新人专享券',10.00,10.00,9999,42,'2026-08-30 15:38:24','2026-09-29 15:38:24',1,'2026-08-30 15:38:24','2026-09-03 20:53:15',0);
/*!40000 ALTER TABLE `coupon` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `order_item`
--

DROP TABLE IF EXISTS `order_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `order_item` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `order_id` bigint unsigned NOT NULL COMMENT 'Order id',
  `product_id` bigint unsigned NOT NULL COMMENT 'Product id',
  `product_name` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Product name snapshot',
  `product_sku` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'SKU snapshot',
  `product_cover_url` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Cover URL snapshot',
  `product_price` decimal(10,2) NOT NULL COMMENT 'Price snapshot',
  `quantity` int NOT NULL COMMENT 'Purchased quantity',
  `subtotal_amount` decimal(10,2) NOT NULL COMMENT 'Item subtotal',
  `sku_spec` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Selected SKU spec snapshot',
  PRIMARY KEY (`id`),
  KEY `idx_order_item_order` (`order_id`),
  KEY `idx_order_item_product` (`product_id`),
  CONSTRAINT `fk_order_item_order` FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`),
  CONSTRAINT `fk_order_item_product` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`),
  CONSTRAINT `chk_order_item_price` CHECK ((`product_price` >= 0)),
  CONSTRAINT `chk_order_item_quantity` CHECK ((`quantity` > 0)),
  CONSTRAINT `chk_order_item_subtotal` CHECK ((`subtotal_amount` >= 0))
) ENGINE=InnoDB AUTO_INCREMENT=81 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Order items';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `order_item`
--

LOCK TABLES `order_item` WRITE;
/*!40000 ALTER TABLE `order_item` DISABLE KEYS */;
INSERT INTO `order_item` VALUES (69,64,1,'红富士苹果','FRESH-APPLE-001','https://super-zzm.oss-cn-beijing.aliyuncs.com/product/2026/8042f6d5b41e423c945912b402865f10.png',12.80,5,64.00,''),(70,64,80,'食用油','GROC-OIL-001','https://super-zzm.oss-cn-beijing.aliyuncs.com/product/2026/c28013fde850403a884391e8d68d8aa9.png',59.90,1,59.90,''),(71,64,85,'酸奶','DAIRY-YOGURT-001','https://super-zzm.oss-cn-beijing.aliyuncs.com/product/2026/1ed2a8cbdae94063a68281773b271568.png',15.90,1,15.90,''),(72,64,88,'黄油','DAIRY-BUTTER-001','https://super-zzm.oss-cn-beijing.aliyuncs.com/product/2026/31f9029f89f84262b2700f23c9bdf7e5.png',22.90,4,91.60,''),(73,65,88,'黄油','DAIRY-BUTTER-001','https://super-zzm.oss-cn-beijing.aliyuncs.com/product/2026/31f9029f89f84262b2700f23c9bdf7e5.png',22.90,11,251.90,''),(74,66,1,'红富士苹果','FRESH-APPLE-001','https://super-zzm.oss-cn-beijing.aliyuncs.com/product/2026/8042f6d5b41e423c945912b402865f10.png',12.80,1,12.80,''),(75,67,84,'纯牛奶','DAIRY-MILK-001','https://super-zzm.oss-cn-beijing.aliyuncs.com/product/2026/c84d2c06a5594aaaba8eb5cf877948e7.png',19.90,5,99.50,''),(76,67,86,'面包','DAIRY-BREAD-001','https://super-zzm.oss-cn-beijing.aliyuncs.com/product/2026/16034ad59670496f8b7b3b91875a086d.png',8.90,5,44.50,''),(77,67,87,'蛋糕','DAIRY-CAKE-001','https://super-zzm.oss-cn-beijing.aliyuncs.com/product/2026/65db9734d1344ecb98fb639f6c19bb6f.png',25.90,4,103.60,''),(78,67,88,'黄油','DAIRY-BUTTER-001','https://super-zzm.oss-cn-beijing.aliyuncs.com/product/2026/31f9029f89f84262b2700f23c9bdf7e5.png',22.90,3,68.70,''),(79,68,81,'生抽','GROC-SOY-001','https://super-zzm.oss-cn-beijing.aliyuncs.com/product/2026/a9f906619f424bf7ac62016ed33d1557.png',8.90,20,178.00,''),(80,68,88,'黄油','DAIRY-BUTTER-001','https://super-zzm.oss-cn-beijing.aliyuncs.com/product/2026/31f9029f89f84262b2700f23c9bdf7e5.png',22.90,10,229.00,'');
/*!40000 ALTER TABLE `order_item` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `orders`
--

DROP TABLE IF EXISTS `orders`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `orders` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `order_no` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Business order number',
  `user_id` bigint unsigned NOT NULL COMMENT 'Buyer user id',
  `total_amount` decimal(10,2) NOT NULL COMMENT 'Product total',
  `freight_amount` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT 'Freight',
  `discount_amount` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT 'Discount',
  `pay_amount` decimal(10,2) NOT NULL COMMENT 'Final amount',
  `status` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING_PAYMENT' COMMENT 'Order status',
  `payment_status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'UNPAID' COMMENT 'UNPAID, PAID, REFUNDED',
  `receiver_name` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Address snapshot',
  `receiver_phone` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Address snapshot',
  `receiver_address` varchar(300) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Address snapshot',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Buyer remark',
  `ship_company` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Express company',
  `ship_no` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Express tracking number',
  `refund_status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'NONE' COMMENT 'NONE, APPLYING, APPROVED, REJECTED',
  `refund_reason` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Buyer refund reason',
  `refund_remark` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Admin review remark',
  `refunded_at` datetime DEFAULT NULL COMMENT 'Refund finished time',
  `user_coupon_id` bigint unsigned DEFAULT NULL COMMENT 'Used user coupon id',
  `paid_at` datetime DEFAULT NULL,
  `shipped_at` datetime DEFAULT NULL,
  `completed_at` datetime DEFAULT NULL,
  `canceled_at` datetime DEFAULT NULL,
  `closed_at` datetime DEFAULT NULL COMMENT 'Timeout close time',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `activity_id` bigint unsigned DEFAULT NULL COMMENT 'Applied activity id',
  `activity_discount` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT 'Activity discount amount',
  `activity_name` varchar(120) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Applied activity name snapshot',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_orders_order_no` (`order_no`),
  KEY `idx_orders_user_status_created` (`user_id`,`status`,`created_at`),
  KEY `idx_orders_status_created` (`status`,`created_at`),
  KEY `idx_orders_refund_status` (`refund_status`),
  KEY `fk_orders_user_coupon` (`user_coupon_id`),
  CONSTRAINT `fk_orders_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`),
  CONSTRAINT `fk_orders_user_coupon` FOREIGN KEY (`user_coupon_id`) REFERENCES `user_coupon` (`id`),
  CONSTRAINT `chk_orders_amounts` CHECK (((`total_amount` >= 0) and (`freight_amount` >= 0) and (`discount_amount` >= 0) and (`pay_amount` >= 0))),
  CONSTRAINT `chk_orders_payment_status` CHECK ((`payment_status` in (_utf8mb4'UNPAID',_utf8mb4'PAID',_utf8mb4'REFUNDED'))),
  CONSTRAINT `chk_orders_refund_status` CHECK ((`refund_status` in (_utf8mb4'NONE',_utf8mb4'APPLYING',_utf8mb4'APPROVED',_utf8mb4'REJECTED'))),
  CONSTRAINT `chk_orders_status` CHECK ((`status` in (_utf8mb4'PENDING_PAYMENT',_utf8mb4'PAID',_utf8mb4'SHIPPED',_utf8mb4'COMPLETED',_utf8mb4'CANCELED',_utf8mb4'CLOSED')))
) ENGINE=InnoDB AUTO_INCREMENT=69 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Orders';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `orders`
--

LOCK TABLES `orders` WRITE;
/*!40000 ALTER TABLE `orders` DISABLE KEYS */;
INSERT INTO `orders` VALUES (64,'202609012201411173EF258',21,231.40,0.00,0.00,181.40,'COMPLETED','PAID','zzm','13592849666','广东汕头潮南12356','前端下单','顺丰','789456','NONE',NULL,NULL,NULL,NULL,'2026-09-01 22:01:41','2026-09-01 22:07:44','2026-09-01 22:07:54',NULL,NULL,'2026-09-01 22:01:41','2026-09-01 22:07:54',26,50.00,'全场满200减50'),(65,'20260901220248058168285',21,251.90,0.00,0.00,201.90,'COMPLETED','PAID','zzm','13592849666','广东汕头潮南12356','前端下单','顺丰','12345789','NONE',NULL,NULL,NULL,NULL,'2026-09-01 22:02:48','2026-09-01 22:06:30','2026-09-01 22:06:38',NULL,NULL,'2026-09-01 22:02:48','2026-09-01 22:06:38',26,50.00,'全场满200减50'),(66,'202609022202241457FD294',21,12.80,0.00,0.00,12.80,'CLOSED','REFUNDED','zzm','13592849666','广东汕头潮南12356','冒烟下单',NULL,NULL,'NONE',NULL,'通过','2026-09-02 22:03:21',NULL,'2026-09-02 22:02:24',NULL,NULL,NULL,NULL,'2026-09-02 22:02:24','2026-09-02 22:21:54',NULL,0.00,NULL),(67,'2026090320582857838289F',21,316.30,0.00,0.00,253.04,'PAID','PAID','zzm','13592849666','广东汕头潮南12356','前端下单',NULL,NULL,'NONE',NULL,NULL,NULL,NULL,'2026-09-03 20:58:29',NULL,NULL,NULL,NULL,'2026-09-03 20:58:28','2026-09-03 20:58:28',27,63.26,'全场满200打8折'),(68,'202609032116423238CBAF1',21,407.00,0.00,25.00,300.60,'PAID','PAID','zzm','13592849666','广东汕头潮南12356','前端下单',NULL,NULL,'NONE',NULL,NULL,NULL,58,'2026-09-03 21:16:42',NULL,NULL,NULL,NULL,'2026-09-03 21:16:42','2026-09-03 21:16:42',27,81.40,'全场满200打8折');
/*!40000 ALTER TABLE `orders` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `page_dwell`
--

DROP TABLE IF EXISTS `page_dwell`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `page_dwell` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `user_id` bigint unsigned DEFAULT NULL COMMENT 'Anonymous when null',
  `product_id` bigint unsigned NOT NULL COMMENT 'Product id',
  `seconds` int NOT NULL COMMENT 'Dwell seconds',
  `source` varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'e.g. detail',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_page_dwell_user` (`user_id`),
  KEY `idx_page_dwell_product` (`product_id`),
  CONSTRAINT `fk_page_dwell_product` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`),
  CONSTRAINT `fk_page_dwell_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=102 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='页面停留时长埋点';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `page_dwell`
--

LOCK TABLES `page_dwell` WRITE;
/*!40000 ALTER TABLE `page_dwell` DISABLE KEYS */;
INSERT INTO `page_dwell` VALUES (39,21,9,3,'detail','2026-08-30 18:45:47'),(40,21,1,4,'detail','2026-08-30 18:45:51'),(41,21,2,4,'detail','2026-08-30 18:45:55'),(42,21,3,5,'detail','2026-08-30 18:46:00'),(43,21,1,8,'detail','2026-08-30 18:46:08'),(44,21,1,15,'detail','2026-08-30 18:47:13'),(48,21,9,3,'detail','2026-08-30 18:51:01'),(49,21,1,6,'detail','2026-08-30 18:51:08'),(53,21,1,4,'detail','2026-08-30 18:51:29'),(54,21,3,3,'detail','2026-08-30 18:51:32'),(55,21,2,3,'detail','2026-08-30 18:51:36'),(56,21,1,3,'detail','2026-08-30 18:51:38'),(57,21,9,4,'detail','2026-08-30 18:51:42'),(66,21,3,3,'detail','2026-08-30 19:21:00'),(67,21,2,3,'detail','2026-08-30 19:21:03'),(68,21,1,3,'detail','2026-08-30 19:21:06'),(69,21,9,3,'detail','2026-08-30 19:21:09'),(75,21,1,4,'detail','2026-08-31 21:13:48'),(76,21,88,8,'detail','2026-08-31 21:58:55'),(77,21,78,13,'detail','2026-08-31 21:59:08'),(78,21,3,4,'detail','2026-08-31 21:59:12'),(79,21,83,7,'detail','2026-08-31 21:59:19'),(80,21,79,7,'detail','2026-08-31 21:59:26'),(81,21,1,8,'detail','2026-08-31 22:09:18'),(91,21,86,20,'detail','2026-09-01 22:01:06'),(92,21,1,3,'detail','2026-09-01 22:01:09'),(93,21,88,5,'detail','2026-09-01 22:06:11'),(94,21,88,31,'detail','2026-09-01 22:07:34'),(95,21,88,5,'detail','2026-09-01 22:08:14'),(96,21,80,5,'detail','2026-09-01 22:08:19'),(97,21,81,6,'detail','2026-09-01 22:08:25'),(98,21,80,20,'detail','2026-09-01 22:08:45'),(99,NULL,88,181,'detail','2026-09-01 22:56:48'),(100,21,1,42,'detail','2026-09-02 22:02:23'),(101,NULL,88,19,'detail','2026-09-03 23:05:38');
/*!40000 ALTER TABLE `page_dwell` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `payment_record`
--

DROP TABLE IF EXISTS `payment_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `payment_record` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `order_id` bigint unsigned NOT NULL COMMENT 'Order id',
  `payment_no` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Payment business number',
  `channel` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'MOCK' COMMENT 'MOCK, ALIPAY, WECHAT',
  `amount` decimal(10,2) NOT NULL,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'SUCCESS' COMMENT 'PENDING, SUCCESS, FAILED, REFUNDED',
  `paid_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payment_record_payment_no` (`payment_no`),
  UNIQUE KEY `uk_payment_record_order` (`order_id`),
  CONSTRAINT `fk_payment_record_order` FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`),
  CONSTRAINT `chk_payment_record_amount` CHECK ((`amount` >= 0)),
  CONSTRAINT `chk_payment_record_status` CHECK ((`status` in (_utf8mb4'PENDING',_utf8mb4'SUCCESS',_utf8mb4'FAILED',_utf8mb4'REFUNDED')))
) ENGINE=InnoDB AUTO_INCREMENT=28 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Payment records';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `payment_record`
--

LOCK TABLES `payment_record` WRITE;
/*!40000 ALTER TABLE `payment_record` DISABLE KEYS */;
INSERT INTO `payment_record` VALUES (23,64,'PAY20260901220141238494640','BALANCE',181.40,'SUCCESS','2026-09-01 22:01:41','2026-09-01 22:01:41','2026-09-01 22:01:41'),(24,65,'PAY2026090122024809915607D','BALANCE',201.90,'SUCCESS','2026-09-01 22:02:48','2026-09-01 22:02:48','2026-09-01 22:02:48'),(25,66,'PAY2026090222022425656A4C2','BALANCE',12.80,'REFUNDED','2026-09-02 22:02:24','2026-09-02 22:02:24','2026-09-02 22:03:20'),(26,67,'PAY20260903205828696FE0796','BALANCE',253.04,'SUCCESS','2026-09-03 20:58:29','2026-09-03 20:58:28','2026-09-03 20:58:28'),(27,68,'PAY2026090321164238143D4D4','BALANCE',300.60,'SUCCESS','2026-09-03 21:16:42','2026-09-03 21:16:42','2026-09-03 21:16:42');
/*!40000 ALTER TABLE `payment_record` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `product`
--

DROP TABLE IF EXISTS `product`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `product` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `category_id` bigint unsigned NOT NULL COMMENT 'Category id',
  `sku` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Stock keeping unit',
  `name` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Product name',
  `subtitle` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Short description',
  `description` text COLLATE utf8mb4_unicode_ci COMMENT 'Product description',
  `cover_url` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Cover image URL',
  `price` decimal(10,2) NOT NULL COMMENT 'Current selling price',
  `original_price` decimal(10,2) DEFAULT NULL COMMENT 'Original price',
  `stock` int NOT NULL DEFAULT '0' COMMENT 'Available stock',
  `low_stock_threshold` int NOT NULL DEFAULT '10' COMMENT 'Alert when stock <= this',
  `sales` int NOT NULL DEFAULT '0' COMMENT 'Sold quantity',
  `unit` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'piece' COMMENT 'piece, kg, box, etc.',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ON_SALE' COMMENT 'ON_SALE, OFF_SALE, DRAFT',
  `brand` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Brand name',
  `is_hot` tinyint NOT NULL DEFAULT '0' COMMENT '1 = hot product',
  `is_new` tinyint NOT NULL DEFAULT '0' COMMENT '1 = new arrival',
  `sort_no` int NOT NULL DEFAULT '0' COMMENT 'Display order',
  `tags` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Comma separated marketing tags',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_product_sku` (`sku`),
  KEY `idx_product_category_status` (`category_id`,`status`),
  KEY `idx_product_name` (`name`),
  CONSTRAINT `fk_product_category` FOREIGN KEY (`category_id`) REFERENCES `product_category` (`id`),
  CONSTRAINT `chk_product_deleted` CHECK ((`deleted` in (0,1))),
  CONSTRAINT `chk_product_original_price` CHECK (((`original_price` is null) or (`original_price` >= 0))),
  CONSTRAINT `chk_product_price` CHECK ((`price` >= 0)),
  CONSTRAINT `chk_product_sales` CHECK ((`sales` >= 0)),
  CONSTRAINT `chk_product_status` CHECK ((`status` in (_utf8mb4'ON_SALE',_utf8mb4'OFF_SALE',_utf8mb4'DRAFT'))),
  CONSTRAINT `chk_product_stock` CHECK ((`stock` >= 0))
) ENGINE=InnoDB AUTO_INCREMENT=89 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Products';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `product`
--

LOCK TABLES `product` WRITE;
/*!40000 ALTER TABLE `product` DISABLE KEYS */;
INSERT INTO `product` VALUES (1,1,'FRESH-APPLE-001','红富士苹果','脆甜多汁 产地直发','精选山东烟台红富士苹果，皮薄肉脆、清甜多汁，适合鲜食与拼盘。','https://super-zzm.oss-cn-beijing.aliyuncs.com/product/2026/8042f6d5b41e423c945912b402865f10.png',12.80,16.00,85,10,1825,'斤','ON_SALE','烟台',1,0,1,'水果,新鲜,甜','2026-08-02 13:13:31','2026-09-02 22:03:20',0),(2,2,'DRINK-WATER-001','矿泉水','天然弱碱 解渴畅饮','天然水源弱碱性矿泉水，口感清冽，运动出行随身带。','https://super-zzm.oss-cn-beijing.aliyuncs.com/product/2026/fa86d6c065114a44939df27d1b51563e.png',2.50,3.12,169,20,2310,'bottle','ON_SALE','农夫山泉',1,0,6,'饮用水,解渴,瓶装','2026-08-02 13:13:31','2026-08-31 21:11:52',0),(3,3,'SNACK-CHIPS-001','原味薯片','酥脆可口 追剧必备','原味薯片，薄脆酥香、口口上瘾，办公室追剧小零嘴。','https://super-zzm.oss-cn-beijing.aliyuncs.com/product/2026/f58bf67f676f46719c5b2bf25a7085ff.png',6.90,8.62,336,20,1990,'bag','ON_SALE','乐事',1,0,11,'零食,膨化,追剧','2026-08-02 13:13:31','2026-08-31 21:12:58',0),(9,1,'FRESH-MANGO-001','芒果','热带香甜 肉厚核小','海南产小台农芒果，果肉金黄、香气浓郁，甜而不腻。','https://super-zzm.oss-cn-beijing.aliyuncs.com/product/2026/80fef4370452409cbf5fd63187aa78c8.png',20.00,NULL,99,10,960,'斤','ON_SALE','海南',0,0,2,'热带,水果,甜','2026-08-29 19:08:25','2026-08-31 21:10:14',0),(63,1,'FRESH-BANANA-001','香蕉','软糯香甜 自然成熟','进口香蕉自然催熟，口感软糯、富含钾元素，老人小孩都爱。','https://super-zzm.oss-cn-beijing.aliyuncs.com/product/2026/ffd7d2ac7f5542129c8ca6a2bd0a9fe3.png',6.90,NULL,120,10,740,'斤','ON_SALE','菲律宾',0,1,3,'水果,粗粮,健康','2026-08-30 19:51:38','2026-09-01 21:45:31',0),(64,1,'FRESH-TOMATO-001','西红柿','沙瓤多汁 凉拌炒菜','自然熟西红柿，皮薄沙瓤、酸甜适口，生吃凉拌或炒菜皆宜。','https://super-zzm.oss-cn-beijing.aliyuncs.com/product/2026/7ef5035fa72c43b4b6ebed8fecc4ab9f.png',5.50,NULL,150,15,610,'斤','ON_SALE','自然熟',0,0,4,'蔬菜,生鲜,沙拉','2026-08-30 19:51:38','2026-08-31 20:58:57',0),(65,1,'FRESH-EGG-001','鸡蛋','新鲜土鸡蛋 营养早餐','新鲜农场鸡蛋，蛋黄饱满、蛋白嫩滑，家庭日常必备。','https://super-zzm.oss-cn-beijing.aliyuncs.com/product/2026/c6f781db795b4d6cb938ed1ca3b001c8.png',15.90,NULL,80,10,520,'box','ON_SALE','正大',0,0,5,'禽蛋,营养,早餐','2026-08-30 19:51:38','2026-08-31 20:59:12',0),(66,2,'DRINK-COLA-001','可乐','经典碳酸 冰爽畅快','经典红罐可乐，气泡充足、冰镇更爽口，聚餐必备。','https://super-zzm.oss-cn-beijing.aliyuncs.com/product/2026/02699e3693c44f7d95b176011e067330.png',3.50,4.38,200,20,1560,'bottle','ON_SALE','可口可乐',1,0,7,'碳酸饮料,冰爽','2026-08-30 19:51:38','2026-08-31 20:59:41',0),(67,2,'DRINK-ORANGE-001','鲜橙汁','真实果肉 维C满满','含真实果肉的鲜橙汁，酸甜可口，补充每日维C。','https://super-zzm.oss-cn-beijing.aliyuncs.com/product/2026/7817b028dcc74924b2c6131019e15843.png',8.90,NULL,110,15,430,'bottle','ON_SALE','美汁源',0,1,8,'果汁,维C,早餐','2026-08-30 19:51:38','2026-08-31 20:59:56',0),(68,2,'DRINK-GREEN-TEA-001','绿茶','无糖零卡 清爽解腻','0 糖 0 卡绿茶饮料，茶香清爽、解腻去油。','https://super-zzm.oss-cn-beijing.aliyuncs.com/product/2026/22004f02bccb4977919b568f00cbd730.png',4.50,NULL,130,15,380,'bottle','ON_SALE','农夫山泉',0,0,9,'茶饮,无糖,零卡','2026-08-30 19:51:38','2026-08-31 21:00:10',0),(69,2,'DRINK-BEER-001','啤酒','麦芽酿造 醇厚泡沫','经典麦芽啤酒，泡沫细腻、口感醇厚，宵夜聚餐好搭档。','https://super-zzm.oss-cn-beijing.aliyuncs.com/product/2026/12d7554f154741e1891d733b31b2b15c.png',6.00,NULL,160,15,690,'bottle','ON_SALE','青岛',0,0,10,'酒类,麦芽,聚餐','2026-08-30 19:51:38','2026-08-31 21:00:34',0),(70,3,'SNACK-CHOCO-001','巧克力','丝滑牛奶 甜而不腻','丝滑牛奶巧克力，入口即化、甜度适中，下午茶小确幸。','https://super-zzm.oss-cn-beijing.aliyuncs.com/product/2026/b8eb1656427b47cfbd429069e3729625.png',12.90,16.12,90,10,870,'box','ON_SALE','德芙',1,0,12,'甜食,巧克力,礼物','2026-08-30 19:51:38','2026-08-31 21:00:52',0),(71,3,'SNACK-SEEDS-001','瓜子','香脆炒货 闲聊零食','香脆瓜子，颗粒饱满、咸香入味，看球闲聊好伴侣。','https://super-zzm.oss-cn-beijing.aliyuncs.com/product/2026/6b25eada4fcc48dc9bf2108d0c16c11b.png',7.50,NULL,140,15,640,'bag','ON_SALE','洽洽',0,0,13,'炒货,休闲,咸香','2026-08-30 19:51:38','2026-08-31 21:02:19',0),(72,3,'SNACK-COOKIE-001','饼干','夹心酥脆 老少皆宜','巧克力夹心饼干，一口酥脆、甜香浓郁，孩子大人都喜欢。','https://super-zzm.oss-cn-beijing.aliyuncs.com/product/2026/90d7b87e980e46a59e15fe9e760d7c64.png',9.90,NULL,100,10,720,'box','ON_SALE','奥利奥',0,1,14,'烘焙,零食,夹心','2026-08-30 19:51:38','2026-08-31 21:02:41',0),(73,3,'SNACK-LATIAO-001','辣条','麻辣过瘾 童年味道','经典辣条，麻辣鲜香、越嚼越上瘾，解馋小零食。','https://super-zzm.oss-cn-beijing.aliyuncs.com/product/2026/bb5d87fbc0ef462f9f94172d499d2f5f.png',3.90,NULL,180,20,1120,'bag','ON_SALE','卫龙',0,0,15,'辣味,小吃,怀旧','2026-08-30 19:51:38','2026-08-31 21:03:35',0),(74,4,'DAILY-TISSUE-001','抽纸','柔软亲肤 居家必备','原生木浆抽纸，柔软不掉屑，家庭日常用纸首选。','https://super-zzm.oss-cn-beijing.aliyuncs.com/product/2026/2affc793ba024552b6fcb8080dd41beb.png',19.90,24.88,200,20,980,'提','ON_SALE','心相印',1,0,16,'纸品,家用,柔软','2026-08-30 19:51:38','2026-08-31 21:03:50',0),(75,4,'DAILY-LAUNDRY-001','洗衣液','深层去污 温和不伤手','高效洗衣液，深层去污、易漂洗，呵护衣物与双手。','https://super-zzm.oss-cn-beijing.aliyuncs.com/product/2026/4e49ac666be64792bc7a63057caaea23.png',29.90,NULL,120,15,560,'bottle','ON_SALE','蓝月亮',0,0,17,'清洁,洗护,衣物','2026-08-30 19:51:38','2026-08-31 21:04:14',0),(76,4,'DAILY-TOOTHPASTE-001','牙膏','清新口气 呵护牙龈','含氟防蛀牙膏，清新薄荷味、呵护牙龈健康。','https://super-zzm.oss-cn-beijing.aliyuncs.com/product/2026/742b652507dd4a33a3b34005c7a7cac8.png',12.90,NULL,150,15,430,'支','ON_SALE','高露洁',0,0,18,'口腔护理,清新','2026-08-30 19:51:38','2026-08-31 21:04:38',0),(77,4,'DAILY-TRASHBAG-001','垃圾袋','加厚结实 不易破漏','加厚垃圾袋，承重力强、不易破裂，厨房卫生间通用。','https://super-zzm.oss-cn-beijing.aliyuncs.com/product/2026/a30fd56ee523420f8989376b0881019c.png',9.90,NULL,220,20,510,'卷','ON_SALE','美丽雅',0,0,19,'日用,清洁,加厚','2026-08-30 19:51:38','2026-08-31 21:04:53',0),(78,4,'DAILY-HANGER-001','衣架','防滑耐用 衣柜整理','PP 材质防滑衣架，轻巧耐用，整齐收纳各类衣物。','https://super-zzm.oss-cn-beijing.aliyuncs.com/product/2026/643e97f7727946eba95a87cd74c0feaa.png',14.90,NULL,130,15,360,'个','ON_SALE','无印',0,1,20,'收纳,家居,防滑','2026-08-30 19:51:38','2026-08-31 21:05:06',0),(79,28,'GROC-RICE-001','大米','颗粒饱满 清香软糯','东北五常稻花香大米，粒粒饱满、蒸饭清香软糯。','https://super-zzm.oss-cn-beijing.aliyuncs.com/product/2026/3faa958cfe2f4f969476ecc019d2ec3d.png',39.90,49.88,90,10,820,'bag','ON_SALE','五常',1,0,21,'主食,米,三餐','2026-08-30 19:51:38','2026-08-31 21:05:26',0),(80,28,'GROC-OIL-001','食用油','非转基因 清淡少油烟','非转基因调和油，清淡少油烟，煎炒烹炸都合适。','https://super-zzm.oss-cn-beijing.aliyuncs.com/product/2026/c28013fde850403a884391e8d68d8aa9.png',59.90,NULL,69,10,471,'桶','ON_SALE','金龙鱼',0,0,22,'食用油,烹饪,健康','2026-08-30 19:51:38','2026-09-01 22:01:41',0),(81,28,'GROC-SOY-001','生抽','酿造酱油 提鲜上色','传统酿造生抽，鲜咸适口、上色自然，凉拌炒菜皆宜。','https://super-zzm.oss-cn-beijing.aliyuncs.com/product/2026/a9f906619f424bf7ac62016ed33d1557.png',8.90,NULL,140,15,630,'bottle','ON_SALE','海天',0,0,23,'调味,酱油,烹饪','2026-08-30 19:51:38','2026-09-03 21:16:42',0),(82,28,'GROC-SALT-001','食盐','精制加碘 日常调味','精制加碘食盐，颗粒细腻、溶解快，家常调味必备。','https://super-zzm.oss-cn-beijing.aliyuncs.com/product/2026/ca08a82d42be43bba0ff503fc579ca0a.png',2.90,NULL,300,30,530,'bag','ON_SALE','中盐',0,0,24,'调味,盐,必备','2026-08-30 19:51:38','2026-08-31 21:07:45',0),(83,28,'GROC-VINEGAR-001','醋','粮食酿造 酸香开胃','粮食酿造食醋，酸香醇厚、凉拌蘸食都开胃。','https://super-zzm.oss-cn-beijing.aliyuncs.com/product/2026/e970e31c0ba64aae84b4f92604946755.png',6.90,NULL,140,15,400,'bottle','ON_SALE','恒顺',0,0,25,'调味,醋,凉拌','2026-08-30 19:51:38','2026-08-31 21:08:28',0),(84,29,'DAIRY-MILK-001','纯牛奶','生牛乳 营养早餐','100% 生牛乳纯牛奶，富含蛋白与钙，早餐温饮皆宜。','https://super-zzm.oss-cn-beijing.aliyuncs.com/product/2026/c84d2c06a5594aaaba8eb5cf877948e7.png',19.90,24.88,175,20,1345,'box','ON_SALE','蒙牛',1,0,26,'乳制品,早餐,补钙','2026-08-30 19:51:38','2026-09-03 20:58:28',0),(85,29,'DAIRY-YOGURT-001','酸奶','浓稠畅轻 活性菌','希腊风味酸奶，浓稠醇厚、富含活性菌，饭后小食。','https://super-zzm.oss-cn-beijing.aliyuncs.com/product/2026/1ed2a8cbdae94063a68281773b271568.png',15.90,NULL,149,15,771,'杯','ON_SALE','安慕希',0,0,27,'乳制品,酸奶,益生菌','2026-08-30 19:51:38','2026-09-01 22:01:41',0),(86,29,'DAIRY-BREAD-001','面包','松软吐司 即食早餐','松软吐司面包，麦香浓郁、口感绵软，匆忙早晨随手拿。','https://super-zzm.oss-cn-beijing.aliyuncs.com/product/2026/16034ad59670496f8b7b3b91875a086d.png',8.90,NULL,115,15,695,'个','ON_SALE','桃李',0,1,28,'烘焙,早餐,吐司','2026-08-30 19:51:38','2026-09-03 20:58:28',0),(87,29,'DAIRY-CAKE-001','蛋糕','鲜奶油 节日甜点','鲜奶油水果蛋糕，绵软香甜、造型精致，生日聚会首选。','https://super-zzm.oss-cn-beijing.aliyuncs.com/product/2026/65db9734d1344ecb98fb639f6c19bb6f.png',25.90,NULL,56,10,354,'box','ON_SALE','好利来',0,0,29,'烘焙,甜点,庆祝','2026-08-30 19:51:38','2026-09-03 20:58:28',0),(88,29,'DAIRY-BUTTER-001','黄油','动物奶油 烘焙原料','动物性黄油，奶香浓郁、易涂抹，烘焙与煎烤好帮手。','https://super-zzm.oss-cn-beijing.aliyuncs.com/product/2026/31f9029f89f84262b2700f23c9bdf7e5.png',22.90,NULL,62,10,328,'box','ON_SALE','安佳',0,0,30,'乳制品,烘焙,涂抹','2026-08-30 19:51:38','2026-09-03 21:16:42',0);
/*!40000 ALTER TABLE `product` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `product_attribute`
--

DROP TABLE IF EXISTS `product_attribute`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `product_attribute` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `product_id` bigint unsigned NOT NULL,
  `attr_name` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `attr_value` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `sort_no` int NOT NULL DEFAULT '0',
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_product_attribute_product` (`product_id`),
  CONSTRAINT `fk_product_attribute_product` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`),
  CONSTRAINT `chk_product_attribute_deleted` CHECK ((`deleted` in (0,1)))
) ENGINE=InnoDB AUTO_INCREMENT=46 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Product attributes / spec table';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `product_attribute`
--

LOCK TABLES `product_attribute` WRITE;
/*!40000 ALTER TABLE `product_attribute` DISABLE KEYS */;
/*!40000 ALTER TABLE `product_attribute` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `product_category`
--

DROP TABLE IF EXISTS `product_category`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `product_category` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `parent_id` bigint unsigned NOT NULL DEFAULT '0' COMMENT 'Parent category, 0 means root',
  `name` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Category name',
  `sort_no` int NOT NULL DEFAULT '0' COMMENT 'Display order',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '1 enabled, 0 disabled',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_product_category_name_parent` (`parent_id`,`name`),
  KEY `idx_product_category_parent_status` (`parent_id`,`status`),
  CONSTRAINT `chk_product_category_deleted` CHECK ((`deleted` in (0,1))),
  CONSTRAINT `chk_product_category_status` CHECK ((`status` in (0,1)))
) ENGINE=InnoDB AUTO_INCREMENT=30 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Product categories';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `product_category`
--

LOCK TABLES `product_category` WRITE;
/*!40000 ALTER TABLE `product_category` DISABLE KEYS */;
INSERT INTO `product_category` VALUES (1,0,'生鲜食品',10,1,'2026-08-02 13:13:31','2026-08-05 22:24:52',0),(2,0,'酒水饮料',20,1,'2026-08-02 13:13:31','2026-08-05 22:24:52',0),(3,0,'休闲零食',30,1,'2026-08-02 13:13:31','2026-08-05 22:24:52',0),(4,0,'日用百货',40,1,'2026-08-02 13:13:31','2026-08-05 22:24:52',0),(28,0,'粮油调味',50,1,'2026-08-30 19:51:38','2026-08-30 19:51:38',0),(29,0,'乳品烘焙',60,1,'2026-08-30 19:51:38','2026-08-30 19:51:38',0);
/*!40000 ALTER TABLE `product_category` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `product_image`
--

DROP TABLE IF EXISTS `product_image`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `product_image` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `product_id` bigint unsigned NOT NULL,
  `url` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL,
  `sort_no` int NOT NULL DEFAULT '0',
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_product_image_product` (`product_id`),
  CONSTRAINT `fk_product_image_product` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`),
  CONSTRAINT `chk_product_image_deleted` CHECK ((`deleted` in (0,1)))
) ENGINE=InnoDB AUTO_INCREMENT=47 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Product image gallery';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `product_image`
--

LOCK TABLES `product_image` WRITE;
/*!40000 ALTER TABLE `product_image` DISABLE KEYS */;
/*!40000 ALTER TABLE `product_image` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `product_review`
--

DROP TABLE IF EXISTS `product_review`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `product_review` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `order_id` bigint unsigned NOT NULL COMMENT 'Source order id',
  `order_item_id` bigint unsigned NOT NULL COMMENT 'Source order item id',
  `product_id` bigint unsigned NOT NULL COMMENT 'Product id',
  `user_id` bigint unsigned NOT NULL COMMENT 'Reviewer user id',
  `rating` tinyint NOT NULL COMMENT '1 to 5 stars',
  `content` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Review text',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `image_urls` varchar(2000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_product_review_order_item` (`order_item_id`),
  KEY `idx_product_review_product_created` (`product_id`,`created_at`),
  KEY `fk_product_review_order` (`order_id`),
  KEY `fk_product_review_user` (`user_id`),
  CONSTRAINT `fk_product_review_order` FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`),
  CONSTRAINT `fk_product_review_order_item` FOREIGN KEY (`order_item_id`) REFERENCES `order_item` (`id`),
  CONSTRAINT `fk_product_review_product` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`),
  CONSTRAINT `fk_product_review_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`),
  CONSTRAINT `chk_product_review_rating` CHECK ((`rating` between 1 and 5))
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Product reviews';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `product_review`
--

LOCK TABLES `product_review` WRITE;
/*!40000 ALTER TABLE `product_review` DISABLE KEYS */;
INSERT INTO `product_review` VALUES (6,65,73,88,21,5,'物流挺快','2026-09-01 22:06:53','2026-09-01 22:06:53',''),(7,64,69,1,21,5,'666','2026-09-01 22:07:58','2026-09-01 22:07:58',''),(8,64,70,80,21,5,'666','2026-09-01 22:07:58','2026-09-01 22:07:58',''),(9,64,71,85,21,5,'666','2026-09-01 22:07:58','2026-09-01 22:07:58',''),(10,64,72,88,21,5,'666','2026-09-01 22:07:58','2026-09-01 22:07:58','');
/*!40000 ALTER TABLE `product_review` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `product_sku`
--

DROP TABLE IF EXISTS `product_sku`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `product_sku` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `product_id` bigint unsigned NOT NULL,
  `spec_json` text COLLATE utf8mb4_unicode_ci,
  `sku_code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `image` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `sort_no` int NOT NULL DEFAULT '0',
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_product_sku_product` (`product_id`),
  CONSTRAINT `fk_product_sku_product` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`),
  CONSTRAINT `chk_product_sku_deleted` CHECK ((`deleted` in (0,1)))
) ENGINE=InnoDB AUTO_INCREMENT=46 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Product SKU / specs';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `product_sku`
--

LOCK TABLES `product_sku` WRITE;
/*!40000 ALTER TABLE `product_sku` DISABLE KEYS */;
/*!40000 ALTER TABLE `product_sku` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `recharge_order`
--

DROP TABLE IF EXISTS `recharge_order`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `recharge_order` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `order_no` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Recharge order business number',
  `user_id` bigint unsigned NOT NULL COMMENT 'Owner user id',
  `amount` decimal(10,2) NOT NULL COMMENT 'Recharge amount',
  `method` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'ALIPAY or WECHAT (simulated)',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING, PAID, CANCELLED, EXPIRED',
  `expire_at` datetime NOT NULL COMMENT 'Order valid until; unpaid after this becomes EXPIRED',
  `paid_at` datetime DEFAULT NULL COMMENT 'Paid time',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_recharge_order_no` (`order_no`),
  KEY `idx_recharge_user` (`user_id`),
  KEY `idx_recharge_status_expire` (`status`,`expire_at`),
  CONSTRAINT `chk_recharge_amount` CHECK ((`amount` > 0)),
  CONSTRAINT `chk_recharge_method` CHECK ((`method` in (_utf8mb4'ALIPAY',_utf8mb4'WECHAT'))),
  CONSTRAINT `chk_recharge_status` CHECK ((`status` in (_utf8mb4'PENDING',_utf8mb4'PAID',_utf8mb4'CANCELLED',_utf8mb4'EXPIRED')))
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Recharge orders (simulated payment)';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `recharge_order`
--

LOCK TABLES `recharge_order` WRITE;
/*!40000 ALTER TABLE `recharge_order` DISABLE KEYS */;
INSERT INTO `recharge_order` VALUES (5,'RC2026082916201549643EAF1',21,100.00,'ALIPAY','CANCELLED','2026-08-29 16:30:15',NULL,'2026-08-29 16:20:15','2026-08-29 16:20:20'),(6,'RC202608291620253164D0385',21,100.00,'ALIPAY','PAID','2026-08-29 16:30:25','2026-08-29 16:20:28','2026-08-29 16:20:25','2026-08-29 16:20:28'),(7,'RC202608291620498952EB542',21,100.00,'WECHAT','PAID','2026-08-29 16:30:50','2026-08-29 16:20:57','2026-08-29 16:20:49','2026-08-29 16:20:57'),(8,'RC2026083017364058643CDF3',21,20000.00,'ALIPAY','PAID','2026-08-30 17:46:41','2026-08-30 17:36:44','2026-08-30 17:36:40','2026-08-30 17:36:43');
/*!40000 ALTER TABLE `recharge_order` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `stock_log`
--

DROP TABLE IF EXISTS `stock_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `stock_log` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `product_id` bigint unsigned NOT NULL COMMENT 'Product id',
  `order_id` bigint unsigned DEFAULT NULL COMMENT 'Related order id',
  `change_quantity` int NOT NULL COMMENT 'Positive in, negative out',
  `stock_before` int NOT NULL COMMENT 'Stock before change',
  `stock_after` int NOT NULL COMMENT 'Stock after change',
  `biz_type` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'INIT, PURCHASE, ORDER_DEDUCT, CANCEL_RETURN, MANUAL',
  `operator_id` bigint unsigned DEFAULT NULL COMMENT 'Operator user id',
  `remark` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_stock_log_product_created` (`product_id`,`created_at`),
  KEY `idx_stock_log_order` (`order_id`),
  KEY `fk_stock_log_operator` (`operator_id`),
  CONSTRAINT `fk_stock_log_operator` FOREIGN KEY (`operator_id`) REFERENCES `sys_user` (`id`),
  CONSTRAINT `fk_stock_log_order` FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`),
  CONSTRAINT `fk_stock_log_product` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`),
  CONSTRAINT `chk_stock_log_stock` CHECK (((`stock_before` >= 0) and (`stock_after` >= 0)))
) ENGINE=InnoDB AUTO_INCREMENT=113 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Inventory change logs';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `stock_log`
--

LOCK TABLES `stock_log` WRITE;
/*!40000 ALTER TABLE `stock_log` DISABLE KEYS */;
INSERT INTO `stock_log` VALUES (37,1,NULL,10,100,110,'PURCHASE',1,'前端入库','2026-08-29 11:45:38'),(38,2,NULL,10,500,510,'PURCHASE',1,'前端入库','2026-08-29 11:45:39'),(39,3,NULL,10,200,210,'PURCHASE',1,'前端入库','2026-08-29 11:45:39'),(100,1,64,-5,90,85,'ORDER_DEDUCT',21,'Order stock deduction','2026-09-01 22:01:41'),(101,80,64,-1,70,69,'ORDER_DEDUCT',21,'Order stock deduction','2026-09-01 22:01:41'),(102,85,64,-1,150,149,'ORDER_DEDUCT',21,'Order stock deduction','2026-09-01 22:01:41'),(103,88,64,-4,90,86,'ORDER_DEDUCT',21,'Order stock deduction','2026-09-01 22:01:41'),(104,88,65,-11,86,75,'ORDER_DEDUCT',21,'Order stock deduction','2026-09-01 22:02:48'),(105,1,66,-1,85,84,'ORDER_DEDUCT',21,'Order stock deduction','2026-09-02 22:02:24'),(107,84,67,-5,180,175,'ORDER_DEDUCT',21,'Order stock deduction','2026-09-03 20:58:28'),(108,86,67,-5,120,115,'ORDER_DEDUCT',21,'Order stock deduction','2026-09-03 20:58:28'),(109,87,67,-4,60,56,'ORDER_DEDUCT',21,'Order stock deduction','2026-09-03 20:58:28'),(110,88,67,-3,75,72,'ORDER_DEDUCT',21,'Order stock deduction','2026-09-03 20:58:28'),(111,81,68,-20,160,140,'ORDER_DEDUCT',21,'Order stock deduction','2026-09-03 21:16:42'),(112,88,68,-10,72,62,'ORDER_DEDUCT',21,'Order stock deduction','2026-09-03 21:16:42');
/*!40000 ALTER TABLE `stock_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_user`
--

DROP TABLE IF EXISTS `sys_user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `username` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Login name',
  `password_hash` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'BCrypt password hash',
  `nickname` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Display name',
  `phone` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Phone number',
  `email` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Email address',
  `role` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'USER' COMMENT 'USER or ADMIN',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '1 enabled, 0 disabled',
  `balance` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT 'Wallet balance',
  `last_login_at` datetime DEFAULT NULL COMMENT 'Last login time',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT 'Logical delete flag',
  `avatar_url` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_user_username` (`username`),
  UNIQUE KEY `uk_sys_user_phone` (`phone`),
  UNIQUE KEY `uk_sys_user_email` (`email`),
  KEY `idx_sys_user_role_status` (`role`,`status`),
  CONSTRAINT `chk_sys_user_role` CHECK ((`role` in (_utf8mb4'USER',_utf8mb4'ADMIN'))),
  CONSTRAINT `chk_sys_user_status` CHECK ((`status` in (0,1)))
) ENGINE=InnoDB AUTO_INCREMENT=85 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Users and administrators';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_user`
--

LOCK TABLES `sys_user` WRITE;
/*!40000 ALTER TABLE `sys_user` DISABLE KEYS */;
INSERT INTO `sys_user` VALUES (1,'admin','$2b$10$7Nv0EV9W1LGnKRIDEbrCz.Uk2.5rCamEEzQvH2aFl48gabhLBRsj.','System Administrator',NULL,NULL,'ADMIN',1,0.00,'2026-09-02 22:53:18','2026-08-02 13:13:31','2026-09-02 22:53:18',0,'https://super-zzm.oss-cn-beijing.aliyuncs.com/avatar/2026/e36918b2d22d49008264ddd3564b0dc6.png'),(2,'testuser_20260802160244','$2a$10$BC4on2YZf8E7SSIwV/zXjOKh.kkHTQnqLhrd6xXYK7h2fg/F1LBLK','Test User',NULL,NULL,'USER',1,0.00,'2026-08-02 16:02:45','2026-08-02 16:02:44','2026-08-02 16:02:44',0,NULL),(3,'cart_user_20260802163946','$2a$10$8BIa6T28P5d7R2Z1k1Z95O/f8Y4lgtdvmdnDai4rpc/YEJ0xJmAfK','购物车测试用户','13800000001',NULL,'USER',1,0.00,'2026-08-02 16:39:48','2026-08-02 16:39:47','2026-08-02 16:39:47',0,NULL),(4,'inspect_cart_20260802164018','$2a$10$esu6oCA8YYK460X38wCg1OXej9FU4OvxRN9eTQjTYrcbUMlWbAQgq','响应检查用户','13800000002',NULL,'USER',1,0.00,'2026-08-02 16:40:19','2026-08-02 16:40:18','2026-08-02 16:40:18',0,NULL),(5,'inspect_cart_31142468','$2a$10$EEvp4HmTHtAs5GExY3BWTu9.yBHdm6jWeyfgQTtMtKblrpiTokiTa','响应检查用户','13931142468',NULL,'USER',1,0.00,'2026-08-02 16:41:45','2026-08-02 16:41:45','2026-08-02 16:41:45',0,NULL),(6,'cart_full_32028271','$2a$10$/xew3/4qm8f9Tu9li07s0uYDdXgBAVaydWFawas5Vt.8wFzcxOWUO','购物车完整测试','13732028271',NULL,'USER',1,0.00,'2026-08-02 16:42:26','2026-08-02 16:42:25','2026-08-02 16:42:25',0,NULL),(7,'order_user_43367354','$2a$10$u0dJYwvuQXUvxUJGrshj9..Wj9at.1HuFYDoBad4YTFl7s/wgrd.G','订单测试用户','13643367354',NULL,'USER',1,0.00,'2026-08-02 17:25:57','2026-08-02 17:25:57','2026-08-02 17:25:57',0,NULL),(8,'pay_flow_81497405','$2a$10$gDar5WdipXFrif1XSOcM/uBKaLam6ru/EwQOmGXqkC7GVDmDAM.Ke','支付取消测试用户','13581497405',NULL,'USER',1,0.00,'2026-08-02 17:32:23','2026-08-02 17:32:22','2026-08-02 17:32:22',0,NULL),(9,'cancel_flow_22504211','$2a$10$XS3ZQ9Ylc1csI76mHf85PuXq3NxpwgrVqyOQDaZoJEPibRZpK6lrC','支付取消测试用户','13522504211',NULL,'USER',1,0.00,'2026-08-02 17:32:27','2026-08-02 17:32:26','2026-08-02 17:32:26',0,NULL),(10,'debug_pay_48885657','$2a$10$.N8rkkhqKpqi9AjRSXiJ9.xc4wHwhJ/FOHCksGC25cuPE/MPKBZ2C','调试用户','13448885657',NULL,'USER',1,0.00,'2026-08-02 17:33:12','2026-08-02 17:33:12','2026-08-02 17:33:12',0,NULL),(11,'debug_cancel_44242120','$2a$10$rUeSCa5cawzKjfMOBxMFxOkkwtaUriAZe60PDPcYOK3pWRbpBkrfS','取消测试用户','13344242120',NULL,'USER',1,0.00,'2026-08-02 17:34:10','2026-08-02 17:34:09','2026-08-02 17:34:09',0,NULL),(12,'pay_flow_64773218','$2a$10$TmNYaMKsPya4n2pDZf1CsuhaM/wegCqk.yudrKjjfYIysk64ZtTgO','支付取消测试用户','13264773218',NULL,'USER',1,0.00,'2026-08-02 17:38:20','2026-08-02 17:38:19','2026-08-02 17:38:20',0,NULL),(13,'cancel_flow_87089994','$2a$10$S/V05HP9/ryjnCWMrXhvKOJV.zd6g65AOm9ufqmVitoblRHKfhBUa','支付取消测试用户','13187089994',NULL,'USER',1,0.00,'2026-08-02 17:38:24','2026-08-02 17:38:24','2026-08-02 17:38:24',0,NULL),(14,'address_user_63611377','$2a$10$6P6yoT0bRS5HhBUVqFHtFuK7IMPwLnJrj9mkf93D8F.bi0rADiQNy','地址测试用户','13063611377',NULL,'USER',1,0.00,'2026-08-02 17:45:14','2026-08-02 17:45:14','2026-08-02 17:45:14',0,NULL),(15,'plain_user_210802605','$2a$10$qoYZTqtiBMDAAPATKFy3IO10swzngPPkx0fcK7uY8MjK4zMxLk6y.','Plain User','13210802605',NULL,'USER',1,0.00,NULL,'2026-08-03 21:08:03','2026-08-03 21:08:03',0,NULL),(16,'admin_order_223610296','$2a$10$52ybN5rx7uFNAtpwNBcc6OlBPE9s779BTJ8VGHRsctelaCRH6hgKi','Admin Order','18223610296',NULL,'ADMIN',1,0.00,'2026-08-05 20:47:43','2026-08-03 22:36:10','2026-08-05 20:47:42',0,NULL),(17,'buyer_order_223610296','$2a$10$NlQ8Bo2Mj4mvkKjTzOTyeeevfg5eQUXCk60cxhXf0eDjzwoT0oWMe','Buyer Order','17223610296',NULL,'USER',1,0.00,NULL,'2026-08-03 22:36:13','2026-08-03 22:36:13',0,NULL),(18,'admin_cat_224253364','$2a$10$KfhEj0CXrnTmR/G5LiybBeVcJnOAfE2Cnh56wt2vxchL.WaEX..IO','Admin Category','16224253364',NULL,'ADMIN',1,0.00,'2026-08-05 20:47:43','2026-08-03 22:42:53','2026-08-05 20:47:42',0,NULL),(19,'admin_user_224936114','$2a$10$KOzmxRxecIg8JOSuMoKrvuyIUXQqBcecIur9FclITNZ7goeWqYTcO','Admin User','15224936114',NULL,'ADMIN',1,0.00,'2026-08-05 20:47:43','2026-08-03 22:49:36','2026-08-05 20:47:42',0,NULL),(20,'target_user_224936114','$2a$10$vt9zYDiGKi7PGvCjHDE6BuoCjb0HciimYgRfqXYwsSBN3kU/HyO.C','Target Updated','13224936114','target_224936114@example.com','ADMIN',0,0.00,NULL,'2026-08-03 22:49:36','2026-08-03 22:49:37',1,NULL),(21,'zzm','$2a$10$Yj2hj6MdS9u967KHy77nkOfXtBzA6buH2vnh3LBykx6O8n4gQyeoW','zzm',NULL,NULL,'USER',1,14377.96,'2026-09-02 22:53:35','2026-08-05 20:42:43','2026-09-03 21:16:42',0,NULL),(22,'wallet_test_20260805212216','$2a$10$itWY/WpBtgYBojtWUy6FpeFWmjZmtV9MqnNF1W9UIR3u6RMffzJf6','wallet_test_20260805212216',NULL,NULL,'USER',1,100.00,NULL,'2026-08-05 21:22:17','2026-08-05 21:22:17',0,NULL),(23,'wallet_test_20260805212336','$2a$10$.j.qmD0wkrF3joUo.unS5.2GyLwzmoSRNk1/AK4IW950duYGudNGm','wallet_test_20260805212336',NULL,NULL,'USER',1,100.00,NULL,'2026-08-05 21:23:36','2026-08-05 21:23:36',0,NULL),(36,'cartck_1788002356','$2a$10$QQsjg4eZF4aVmA61d5FTv.gATrEMxWE5T7qRzHb8vT6Re.6xoaKZS','ck',NULL,NULL,'USER',0,0.00,'2026-08-29 19:19:17','2026-08-29 19:19:17','2026-08-29 19:19:17',1,NULL),(37,'stocktest_1788013422674','$2a$10$ujbWXqCoj3F8hZMGVelZh.v9Xsg245dDuV9BixbDoMdADgb4lDqOy','stocktest_1788013422674',NULL,NULL,'USER',0,0.00,NULL,'2026-08-29 22:23:42','2026-08-29 22:24:24',1,NULL),(38,'imgtest_8527','$2a$10$2OcNxM51Q.pMioEdlw0vROvH9TCPWzPPUDnLIWazeKp3eATIvBEf6','imgtest_8527',NULL,NULL,'USER',0,0.00,'2026-08-29 23:02:18','2026-08-29 23:02:17','2026-08-29 23:02:20',1,'/api/uploads/avatar/2026/c9211e204e0b415a84ecf19e02653df2.png'),(39,'imgtest_8635','$2a$10$P0hnm1X2HHxhmoV8sRwjM.ygH5TsxEkaLcsXUgJ2jn6BJi9V77kPO','imgtest_8635',NULL,NULL,'USER',0,0.00,'2026-08-29 23:03:14','2026-08-29 23:03:13','2026-08-29 23:03:15',1,''),(40,'imgtest_8719','$2a$10$pUFnuU73n3qjD7ajNYJehebT9mm/NQZ962sT6xg6/1trGJDhTw4l6','imgtest_8719',NULL,NULL,'USER',0,0.00,'2026-08-29 23:04:07','2026-08-29 23:04:06','2026-08-29 23:04:48',1,'/api/uploads/avatar/2026/946aebc343574b718ea3e28af70c580e.png'),(41,'imgtest_8829','$2a$10$NaDRfrDzewfzc4/Qu5V8zuIpWm9yICBiuXsRrWwLVQxgL2KKQ5JT2','imgtest_8829',NULL,NULL,'USER',0,0.00,'2026-08-29 23:04:27','2026-08-29 23:04:27','2026-08-29 23:04:48',1,'/api/uploads/avatar/2026/5f1e36733ee744daad5417e7156b4632.png'),(42,'test403u','$2a$10$EBJziB6m6715rQBx/FnAcOKkqjCBeZYOcG25FjHG.lp4L9OtiSvVi','test403u',NULL,NULL,'USER',0,0.00,'2026-08-30 13:48:35','2026-08-30 13:48:34','2026-08-30 13:54:13',1,NULL),(43,'diag_1788072381','$2a$10$BNOsJyETeRNLj2Cl0wys1.DT9YoECfIHLm1KffmCvlSAS65A9USYe','diag','13800000000','diag_1788072381@example.com','USER',1,0.00,'2026-08-30 15:15:44','2026-08-30 14:46:22','2026-08-30 15:15:44',0,NULL),(44,'newbuyer_1788075668','$2a$10$oj0LoA/QO7TAqRwiViQtL.JfpnRAiPqQ.JwwB.hWosv08F.rwh3pC','新人','13812345678','newbuyer_1788075668@example.com','USER',1,0.00,NULL,'2026-08-30 15:41:09','2026-08-30 15:41:09',0,NULL),(45,'newbuyer_1788075670_2','$2a$10$NIfd7XCmXov7UrQxSsqFtOBonlSF/b52eBTYGsmiYzYtpJwvfUOkS','newbuyer_1788075670_2','13900001111',NULL,'USER',1,0.00,NULL,'2026-08-30 15:41:11','2026-08-30 15:41:11',0,NULL),(46,'fresh_7788','$2a$10$iK27teYeWQo2cMOORZRhf.PO.LBS1/DSCG3GYSnYbRKEgmIffoqQu','新人','13900003333','fresh7788@example.com','USER',1,0.00,NULL,'2026-08-30 15:42:11','2026-08-30 15:42:11',0,NULL),(47,'test_u_1788075899','$2a$10$zp0P83gIYOSBrj9q139gwOODazftIRUzySH2GuvMbrKb.9iXU8PFO','test_u_1788075899','13930457785',NULL,'USER',1,0.00,NULL,'2026-08-30 15:45:00','2026-08-30 15:45:00',0,NULL),(48,'test_u_1788075916','$2a$10$e9eofBud5wYYHepRN4akEe0GZc.qchIXJ/YvMIOCIstrnB461/Iw6','test_u_1788075916','13960183723',NULL,'USER',1,0.00,NULL,'2026-08-30 15:45:17','2026-08-30 15:45:17',0,NULL),(49,'skuspec_1788080199','$2a$10$M6Sc70wDqBDzuOLN9vx7pugMBb2hfKHk7AvVA5IlWAgv6MDQ6joc.','sku_spec_tester','13788080199',NULL,'USER',0,0.00,NULL,'2026-08-30 16:56:39','2026-08-30 16:56:40',1,NULL),(50,'uisku_1788080396','$2a$10$r9a9dvZT0UZPqpXnbOMQvegfq8sZwKsdst3tpW8BiO5fZgjxPkpHy','uisku_1788080396','13788080396','uisku_1788080396@test.com','USER',0,0.00,'2026-08-30 17:08:33','2026-08-30 17:00:39','2026-08-30 17:08:32',1,NULL),(66,'dbg_1788086988','$2a$10$4YH4JTnj2Nl7.XFz6N7.KuJn/7J78y2gujNKZ9ztWg1utiSbJMnHa','DBG','13900000001','dbg@example.com','USER',1,0.00,NULL,'2026-08-30 18:49:48','2026-08-30 18:49:48',0,NULL),(68,'dbg2_1788087037','$2a$10$0f.CvUYgt3GngmRdx/CsJuCp80LJx/wwqTU6e7r2Zeiy7XNfPPqvG','DBG2','13700000002','d2@example.com','USER',1,0.00,NULL,'2026-08-30 18:50:38','2026-08-30 18:50:38',0,NULL),(79,'sz_972504338','$2a$10$iFiBSQEA8ixF156hTbLQQO5MtO.smlpnuZD5FyyLM7C7Vg9KbnFJW','t','13867270851','sz_972504338@t.com','USER',1,0.00,NULL,'2026-08-31 20:52:01','2026-08-31 20:52:01',0,NULL),(80,'smk_verify_1788266802','$2a$10$ybvWMqFMAXNotAXlsSahp.TInT1KVKbxtZQPlyfSq490tz99x8qFC','验证员','13500003117','smk_verify_1788266802@test.com','USER',1,0.00,NULL,'2026-09-01 20:46:42','2026-09-01 20:46:42',0,NULL),(81,'smk_verify_1788266896','$2a$10$z771lLdDzb0VF7iSuF89MOyCUP0DjuDgIUYN7AwFd18VpzkxogwyK','验证员','13500031353','smk_verify_1788266896@test.com','USER',1,0.00,NULL,'2026-09-01 20:48:16','2026-09-01 20:48:16',0,NULL),(82,'verifyadmin','$2a$10$yYTLtM9i0hDJBpl.60SCYefZWBsIXT1Tig5l2rg9jfJjirxRo2Pba','验证管理员','13612340000',NULL,'ADMIN',1,0.00,'2026-09-01 23:01:14','2026-09-01 22:56:01','2026-09-01 23:01:14',0,NULL);
/*!40000 ALTER TABLE `sys_user` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `user_address`
--

DROP TABLE IF EXISTS `user_address`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_address` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `user_id` bigint unsigned NOT NULL COMMENT 'Owner user id',
  `receiver_name` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Receiver name',
  `receiver_phone` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Receiver phone',
  `province` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `city` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `district` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `detail_address` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `is_default` tinyint NOT NULL DEFAULT '0' COMMENT '1 default, 0 not default',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_address_user` (`user_id`),
  CONSTRAINT `fk_user_address_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`),
  CONSTRAINT `chk_user_address_default` CHECK ((`is_default` in (0,1)))
) ENGINE=InnoDB AUTO_INCREMENT=30 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='User delivery addresses';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `user_address`
--

LOCK TABLES `user_address` WRITE;
/*!40000 ALTER TABLE `user_address` DISABLE KEYS */;
INSERT INTO `user_address` VALUES (1,7,'Order Tester','13643367354','Guangdong','Shenzhen','Nanshan','No. 1 Supermarket Road',1,'2026-08-02 17:25:59','2026-08-02 17:25:59'),(2,8,'Flow Tester','13581497405','Guangdong','Shenzhen','Nanshan','No. 2 Supermarket Road',1,'2026-08-02 17:32:24','2026-08-02 17:32:24'),(3,9,'Flow Tester','13522504211','Guangdong','Shenzhen','Nanshan','No. 2 Supermarket Road',1,'2026-08-02 17:32:26','2026-08-02 17:32:26'),(4,10,'Debug Tester','13448885657','Guangdong','Shenzhen','Nanshan','No. 3 Road',1,'2026-08-02 17:33:14','2026-08-02 17:33:14'),(5,11,'Cancel Tester','13344242120','Guangdong','Shenzhen','Nanshan','No. 4 Road',1,'2026-08-02 17:34:11','2026-08-02 17:34:11'),(6,12,'Flow Tester','13264773218','Guangdong','Shenzhen','Nanshan','No. 2 Supermarket Road',1,'2026-08-02 17:38:22','2026-08-02 17:38:22'),(7,13,'Flow Tester','13187089994','Guangdong','Shenzhen','Nanshan','No. 2 Supermarket Road',1,'2026-08-02 17:38:24','2026-08-02 17:38:24'),(8,14,'Zhang San','13063611377','Guangdong','Shenzhen','Nanshan','No. 1 Supermarket Road',1,'2026-08-02 17:45:14','2026-08-02 17:45:14'),(10,17,'Smoke Buyer','13900000000','TestProvince','TestCity','TestDistrict','No. 1 Road',1,'2026-08-03 22:36:13','2026-08-03 22:36:13'),(11,21,'zzm','13592849666','广东','汕头','潮南','12356',1,'2026-08-05 20:43:47','2026-08-05 20:43:47'),(12,22,'Tester','13800000000','Test','Test','Test','Wallet smoke test',1,'2026-08-05 21:22:17','2026-08-05 21:22:17'),(13,23,'Tester','13800000000','Test','Test','Test','Wallet smoke test',1,'2026-08-05 21:23:36','2026-08-05 21:23:36'),(29,81,'张三','13800138000','广东省','深圳市','南山区','科技园路 1 号',1,'2026-09-01 20:49:47','2026-09-01 20:49:47');
/*!40000 ALTER TABLE `user_address` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `user_coupon`
--

DROP TABLE IF EXISTS `user_coupon`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_coupon` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `coupon_id` bigint unsigned NOT NULL COMMENT 'Coupon id',
  `user_id` bigint unsigned NOT NULL COMMENT 'Owner user id',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'UNUSED' COMMENT 'UNUSED, USED, EXPIRED',
  `order_id` bigint unsigned DEFAULT NULL COMMENT 'Order that consumed this coupon',
  `received_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `used_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_coupon_user_coupon` (`user_id`,`coupon_id`),
  KEY `idx_user_coupon_user_status` (`user_id`,`status`),
  KEY `fk_user_coupon_coupon` (`coupon_id`),
  KEY `fk_user_coupon_order` (`order_id`),
  CONSTRAINT `fk_user_coupon_coupon` FOREIGN KEY (`coupon_id`) REFERENCES `coupon` (`id`),
  CONSTRAINT `fk_user_coupon_order` FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`),
  CONSTRAINT `fk_user_coupon_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`),
  CONSTRAINT `chk_user_coupon_status` CHECK ((`status` in (_utf8mb4'UNUSED',_utf8mb4'USED',_utf8mb4'EXPIRED')))
) ENGINE=InnoDB AUTO_INCREMENT=59 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='User received coupons';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `user_coupon`
--

LOCK TABLES `user_coupon` WRITE;
/*!40000 ALTER TABLE `user_coupon` DISABLE KEYS */;
INSERT INTO `user_coupon` VALUES (15,5,44,'UNUSED',NULL,'2026-08-30 15:41:09',NULL,'2026-08-30 15:41:09','2026-08-30 15:41:09'),(16,5,45,'UNUSED',NULL,'2026-08-30 15:41:11',NULL,'2026-08-30 15:41:11','2026-08-30 15:41:11'),(17,5,46,'UNUSED',NULL,'2026-08-30 15:42:12',NULL,'2026-08-30 15:42:11','2026-08-30 15:42:11'),(18,5,47,'UNUSED',NULL,'2026-08-30 15:45:00',NULL,'2026-08-30 15:45:00','2026-08-30 15:45:00'),(19,5,48,'UNUSED',NULL,'2026-08-30 15:45:17',NULL,'2026-08-30 15:45:17','2026-08-30 15:45:17'),(20,5,49,'UNUSED',NULL,'2026-08-30 16:56:39',NULL,'2026-08-30 16:56:39','2026-08-30 16:56:39'),(21,5,50,'UNUSED',NULL,'2026-08-30 17:00:39',NULL,'2026-08-30 17:00:39','2026-08-30 17:00:39'),(38,5,66,'UNUSED',NULL,'2026-08-30 18:49:48',NULL,'2026-08-30 18:49:48','2026-08-30 18:49:48'),(40,5,68,'UNUSED',NULL,'2026-08-30 18:50:38',NULL,'2026-08-30 18:50:38','2026-08-30 18:50:38'),(51,5,79,'UNUSED',NULL,'2026-08-31 20:52:02',NULL,'2026-08-31 20:52:01','2026-08-31 20:52:01'),(52,5,80,'UNUSED',NULL,'2026-09-01 20:46:43',NULL,'2026-09-01 20:46:42','2026-09-01 20:46:42'),(53,5,81,'UNUSED',NULL,'2026-09-01 20:48:17',NULL,'2026-09-01 20:48:16','2026-09-01 20:48:16'),(54,5,82,'UNUSED',NULL,'2026-09-01 22:56:01',NULL,'2026-09-01 22:56:01','2026-09-01 22:56:01'),(58,2,21,'USED',68,'2026-09-03 21:00:24','2026-09-03 21:16:42','2026-09-03 21:00:24','2026-09-03 21:16:42');
/*!40000 ALTER TABLE `user_coupon` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `wallet_transaction`
--

DROP TABLE IF EXISTS `wallet_transaction`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `wallet_transaction` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `transaction_no` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Wallet transaction number',
  `user_id` bigint unsigned NOT NULL COMMENT 'Owner user id',
  `order_id` bigint unsigned DEFAULT NULL COMMENT 'Related order id',
  `type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'RECHARGE, PAYMENT, REFUND',
  `amount` decimal(10,2) NOT NULL COMMENT 'Transaction amount',
  `balance_before` decimal(10,2) NOT NULL COMMENT 'Balance before transaction',
  `balance_after` decimal(10,2) NOT NULL COMMENT 'Balance after transaction',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'SUCCESS' COMMENT 'SUCCESS, FAILED',
  `remark` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_wallet_transaction_no` (`transaction_no`),
  KEY `idx_wallet_user_created` (`user_id`,`created_at`),
  KEY `idx_wallet_order` (`order_id`),
  CONSTRAINT `fk_wallet_transaction_order` FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`),
  CONSTRAINT `fk_wallet_transaction_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`),
  CONSTRAINT `chk_wallet_transaction_amount` CHECK ((`amount` > 0)),
  CONSTRAINT `chk_wallet_transaction_balance` CHECK (((`balance_before` >= 0) and (`balance_after` >= 0))),
  CONSTRAINT `chk_wallet_transaction_status` CHECK ((`status` in (_gbk'SUCCESS',_gbk'FAILED'))),
  CONSTRAINT `chk_wallet_transaction_type` CHECK ((`type` in (_gbk'RECHARGE',_gbk'PAYMENT',_gbk'REFUND')))
) ENGINE=InnoDB AUTO_INCREMENT=46 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Wallet transaction logs';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `wallet_transaction`
--

LOCK TABLES `wallet_transaction` WRITE;
/*!40000 ALTER TABLE `wallet_transaction` DISABLE KEYS */;
INSERT INTO `wallet_transaction` VALUES (1,'WT202608052122176416BB66A',22,NULL,'RECHARGE',100.00,0.00,100.00,'SUCCESS','User recharge','2026-08-05 21:22:17'),(2,'WT20260805212336389ACE89D',23,NULL,'RECHARGE',100.00,0.00,100.00,'SUCCESS','User recharge','2026-08-05 21:23:36'),(5,'WT202608052126007972251B1',21,NULL,'RECHARGE',10.00,0.00,10.00,'SUCCESS','User recharge','2026-08-05 21:26:00'),(18,'WT202608291447142234F6E20',21,NULL,'RECHARGE',10.00,0.10,10.10,'SUCCESS','User recharge','2026-08-29 14:47:14'),(23,'WT20260829162028503E6444A',21,NULL,'RECHARGE',100.00,10.10,110.10,'SUCCESS','充值订单到账','2026-08-29 16:20:28'),(24,'WT2026082916205737768D54C',21,NULL,'RECHARGE',100.00,110.10,210.10,'SUCCESS','充值订单到账','2026-08-29 16:20:57'),(34,'WT20260830173643844B0B018',21,NULL,'RECHARGE',20000.00,102.40,20102.40,'SUCCESS','充值订单到账','2026-08-30 17:36:43'),(40,'WT20260901220141228BF38A3',21,64,'PAYMENT',181.40,15314.90,15133.50,'SUCCESS','Order payment','2026-09-01 22:01:41'),(41,'WT2026090122024809347ABA8',21,65,'PAYMENT',201.90,15133.50,14931.60,'SUCCESS','Order payment','2026-09-01 22:02:48'),(42,'WT20260902220224239ED56FE',21,66,'PAYMENT',12.80,14931.60,14918.80,'SUCCESS','Order payment','2026-09-02 22:02:24'),(43,'WT2026090222032094865717C',21,66,'REFUND',12.80,14918.80,14931.60,'SUCCESS','Refund approved','2026-09-02 22:03:20'),(44,'WT20260903205828686AB6817',21,67,'PAYMENT',253.04,14931.60,14678.56,'SUCCESS','Order payment','2026-09-03 20:58:28'),(45,'WT20260903211642377A22C01',21,68,'PAYMENT',300.60,14678.56,14377.96,'SUCCESS','Order payment','2026-09-03 21:16:42');
/*!40000 ALTER TABLE `wallet_transaction` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping events for database 'supermarket_system'
--

--
-- Dumping routines for database 'supermarket_system'
--
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-09-04 21:01:32
