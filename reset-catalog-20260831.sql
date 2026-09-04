-- ============================================================
-- 商品目录初始数据快照 / 一键恢复脚本
-- 生成时间 : 2026-08-31 (当前 6 分类 / 30 商品 / 含已上传 OSS 封面)
-- 用途     : 测试前后，将商品目录恢复到「初始状态」
-- 用法     : mysql -uroot -pzzmsgz supermarket_system < deploy/reset-catalog-20260831.sql
-- 说明     : 仅重置商品目录相关表
--             (product_category, product, product_sku, product_image, product_review)
--            不影响 users / orders / coupons / wallet 等其他业务数据。
--            如需更新初始数据，请重新生成本文件即可。
-- ============================================================
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS=0;

-- 清空现有商品目录数据 (逆序删除，避免外键阻塞)
DELETE FROM product_review;
DELETE FROM product_image;
DELETE FROM product_sku;
DELETE FROM product;
DELETE FROM product_category;

SET FOREIGN_KEY_CHECKS=1;

-- ===== 以下为当前快照数据 (由 mysqldump 生成) =====

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

LOCK TABLES `product_category` WRITE;
/*!40000 ALTER TABLE `product_category` DISABLE KEYS */;
INSERT INTO `product_category` (`id`, `parent_id`, `name`, `sort_no`, `status`, `created_at`, `updated_at`, `deleted`) VALUES (1,0,'生鲜食品',10,1,'2026-08-02 13:13:31','2026-08-05 22:24:52',0),(2,0,'酒水饮料',20,1,'2026-08-02 13:13:31','2026-08-05 22:24:52',0),(3,0,'休闲零食',30,1,'2026-08-02 13:13:31','2026-08-05 22:24:52',0),(4,0,'日用百货',40,1,'2026-08-02 13:13:31','2026-08-05 22:24:52',0),(28,0,'粮油调味',50,1,'2026-08-30 19:51:38','2026-08-30 19:51:38',0),(29,0,'乳品烘焙',60,1,'2026-08-30 19:51:38','2026-08-30 19:51:38',0);
/*!40000 ALTER TABLE `product_category` ENABLE KEYS */;
UNLOCK TABLES;

LOCK TABLES `product` WRITE;
/*!40000 ALTER TABLE `product` DISABLE KEYS */;
INSERT INTO `product` (`id`, `category_id`, `sku`, `name`, `subtitle`, `description`, `cover_url`, `price`, `original_price`, `stock`, `low_stock_threshold`, `sales`, `unit`, `status`, `brand`, `is_hot`, `is_new`, `sort_no`, `tags`, `created_at`, `updated_at`, `deleted`) VALUES (1,1,'FRESH-APPLE-001','红富士苹果','脆甜多汁 产地直发','精选山东烟台红富士苹果，皮薄肉脆、清甜多汁，适合鲜食与拼盘。','https://super-zzm.oss-cn-beijing.aliyuncs.com/product/2026/8042f6d5b41e423c945912b402865f10.png',12.80,16.00,90,10,1820,'斤','ON_SALE','烟台',1,0,1,'水果,新鲜,甜','2026-08-02 13:13:31','2026-08-31 21:11:19',0),(2,2,'DRINK-WATER-001','矿泉水','天然弱碱 解渴畅饮','天然水源弱碱性矿泉水，口感清冽，运动出行随身带。','https://super-zzm.oss-cn-beijing.aliyuncs.com/product/2026/fa86d6c065114a44939df27d1b51563e.png',2.50,3.12,169,20,2310,'bottle','ON_SALE','农夫山泉',1,0,6,'饮用水,解渴,瓶装','2026-08-02 13:13:31','2026-08-31 21:11:52',0),(3,3,'SNACK-CHIPS-001','原味薯片','酥脆可口 追剧必备','原味薯片，薄脆酥香、口口上瘾，办公室追剧小零嘴。','https://super-zzm.oss-cn-beijing.aliyuncs.com/product/2026/f58bf67f676f46719c5b2bf25a7085ff.png',6.90,8.62,336,20,1990,'bag','ON_SALE','乐事',1,0,11,'零食,膨化,追剧','2026-08-02 13:13:31','2026-08-31 21:12:58',0),(9,1,'FRESH-MANGO-001','芒果','热带香甜 肉厚核小','海南产小台农芒果，果肉金黄、香气浓郁，甜而不腻。','https://super-zzm.oss-cn-beijing.aliyuncs.com/product/2026/80fef4370452409cbf5fd63187aa78c8.png',20.00,NULL,99,10,960,'斤','ON_SALE','海南',0,0,2,'热带,水果,甜','2026-08-29 19:08:25','2026-08-31 21:10:14',0),(63,1,'FRESH-BANANA-001','香蕉','软糯香甜 自然成熟','进口香蕉自然催熟，口感软糯、富含钾元素，老人小孩都爱。','https://super-zzm.oss-cn-beijing.aliyuncs.com/product/2026/ffd7d2ac7f5542129c8ca6a2bd0a9fe3.png',6.90,NULL,120,10,740,'斤','ON_SALE','菲律宾',0,1,3,'水果,粗粮,健康','2026-08-30 19:51:38','2026-08-31 20:56:23',0),(64,1,'FRESH-TOMATO-001','西红柿','沙瓤多汁 凉拌炒菜','自然熟西红柿，皮薄沙瓤、酸甜适口，生吃凉拌或炒菜皆宜。','https://super-zzm.oss-cn-beijing.aliyuncs.com/product/2026/7ef5035fa72c43b4b6ebed8fecc4ab9f.png',5.50,NULL,150,15,610,'斤','ON_SALE','自然熟',0,0,4,'蔬菜,生鲜,沙拉','2026-08-30 19:51:38','2026-08-31 20:58:57',0),(65,1,'FRESH-EGG-001','鸡蛋','新鲜土鸡蛋 营养早餐','新鲜农场鸡蛋，蛋黄饱满、蛋白嫩滑，家庭日常必备。','https://super-zzm.oss-cn-beijing.aliyuncs.com/product/2026/c6f781db795b4d6cb938ed1ca3b001c8.png',15.90,NULL,80,10,520,'box','ON_SALE','正大',0,0,5,'禽蛋,营养,早餐','2026-08-30 19:51:38','2026-08-31 20:59:12',0),(66,2,'DRINK-COLA-001','可乐','经典碳酸 冰爽畅快','经典红罐可乐，气泡充足、冰镇更爽口，聚餐必备。','https://super-zzm.oss-cn-beijing.aliyuncs.com/product/2026/02699e3693c44f7d95b176011e067330.png',3.50,4.38,200,20,1560,'bottle','ON_SALE','可口可乐',1,0,7,'碳酸饮料,冰爽','2026-08-30 19:51:38','2026-08-31 20:59:41',0),(67,2,'DRINK-ORANGE-001','鲜橙汁','真实果肉 维C满满','含真实果肉的鲜橙汁，酸甜可口，补充每日维C。','https://super-zzm.oss-cn-beijing.aliyuncs.com/product/2026/7817b028dcc74924b2c6131019e15843.png',8.90,NULL,110,15,430,'bottle','ON_SALE','美汁源',0,1,8,'果汁,维C,早餐','2026-08-30 19:51:38','2026-08-31 20:59:56',0),(68,2,'DRINK-GREEN-TEA-001','绿茶','无糖零卡 清爽解腻','0 糖 0 卡绿茶饮料，茶香清爽、解腻去油。','https://super-zzm.oss-cn-beijing.aliyuncs.com/product/2026/22004f02bccb4977919b568f00cbd730.png',4.50,NULL,130,15,380,'bottle','ON_SALE','农夫山泉',0,0,9,'茶饮,无糖,零卡','2026-08-30 19:51:38','2026-08-31 21:00:10',0),(69,2,'DRINK-BEER-001','啤酒','麦芽酿造 醇厚泡沫','经典麦芽啤酒，泡沫细腻、口感醇厚，宵夜聚餐好搭档。','https://super-zzm.oss-cn-beijing.aliyuncs.com/product/2026/12d7554f154741e1891d733b31b2b15c.png',6.00,NULL,160,15,690,'bottle','ON_SALE','青岛',0,0,10,'酒类,麦芽,聚餐','2026-08-30 19:51:38','2026-08-31 21:00:34',0),(70,3,'SNACK-CHOCO-001','巧克力','丝滑牛奶 甜而不腻','丝滑牛奶巧克力，入口即化、甜度适中，下午茶小确幸。','https://super-zzm.oss-cn-beijing.aliyuncs.com/product/2026/b8eb1656427b47cfbd429069e3729625.png',12.90,16.12,90,10,870,'box','ON_SALE','德芙',1,0,12,'甜食,巧克力,礼物','2026-08-30 19:51:38','2026-08-31 21:00:52',0),(71,3,'SNACK-SEEDS-001','瓜子','香脆炒货 闲聊零食','香脆瓜子，颗粒饱满、咸香入味，看球闲聊好伴侣。','https://super-zzm.oss-cn-beijing.aliyuncs.com/product/2026/6b25eada4fcc48dc9bf2108d0c16c11b.png',7.50,NULL,140,15,640,'bag','ON_SALE','洽洽',0,0,13,'炒货,休闲,咸香','2026-08-30 19:51:38','2026-08-31 21:02:19',0),(72,3,'SNACK-COOKIE-001','饼干','夹心酥脆 老少皆宜','巧克力夹心饼干，一口酥脆、甜香浓郁，孩子大人都喜欢。','https://super-zzm.oss-cn-beijing.aliyuncs.com/product/2026/90d7b87e980e46a59e15fe9e760d7c64.png',9.90,NULL,100,10,720,'box','ON_SALE','奥利奥',0,1,14,'烘焙,零食,夹心','2026-08-30 19:51:38','2026-08-31 21:02:41',0),(73,3,'SNACK-LATIAO-001','辣条','麻辣过瘾 童年味道','经典辣条，麻辣鲜香、越嚼越上瘾，解馋小零食。','https://super-zzm.oss-cn-beijing.aliyuncs.com/product/2026/bb5d87fbc0ef462f9f94172d499d2f5f.png',3.90,NULL,180,20,1120,'bag','ON_SALE','卫龙',0,0,15,'辣味,小吃,怀旧','2026-08-30 19:51:38','2026-08-31 21:03:35',0),(74,4,'DAILY-TISSUE-001','抽纸','柔软亲肤 居家必备','原生木浆抽纸，柔软不掉屑，家庭日常用纸首选。','https://super-zzm.oss-cn-beijing.aliyuncs.com/product/2026/2affc793ba024552b6fcb8080dd41beb.png',19.90,24.88,200,20,980,'提','ON_SALE','心相印',1,0,16,'纸品,家用,柔软','2026-08-30 19:51:38','2026-08-31 21:03:50',0),(75,4,'DAILY-LAUNDRY-001','洗衣液','深层去污 温和不伤手','高效洗衣液，深层去污、易漂洗，呵护衣物与双手。','https://super-zzm.oss-cn-beijing.aliyuncs.com/product/2026/4e49ac666be64792bc7a63057caaea23.png',29.90,NULL,120,15,560,'bottle','ON_SALE','蓝月亮',0,0,17,'清洁,洗护,衣物','2026-08-30 19:51:38','2026-08-31 21:04:14',0),(76,4,'DAILY-TOOTHPASTE-001','牙膏','清新口气 呵护牙龈','含氟防蛀牙膏，清新薄荷味、呵护牙龈健康。','https://super-zzm.oss-cn-beijing.aliyuncs.com/product/2026/742b652507dd4a33a3b34005c7a7cac8.png',12.90,NULL,150,15,430,'支','ON_SALE','高露洁',0,0,18,'口腔护理,清新','2026-08-30 19:51:38','2026-08-31 21:04:38',0),(77,4,'DAILY-TRASHBAG-001','垃圾袋','加厚结实 不易破漏','加厚垃圾袋，承重力强、不易破裂，厨房卫生间通用。','https://super-zzm.oss-cn-beijing.aliyuncs.com/product/2026/a30fd56ee523420f8989376b0881019c.png',9.90,NULL,220,20,510,'卷','ON_SALE','美丽雅',0,0,19,'日用,清洁,加厚','2026-08-30 19:51:38','2026-08-31 21:04:53',0),(78,4,'DAILY-HANGER-001','衣架','防滑耐用 衣柜整理','PP 材质防滑衣架，轻巧耐用，整齐收纳各类衣物。','https://super-zzm.oss-cn-beijing.aliyuncs.com/product/2026/643e97f7727946eba95a87cd74c0feaa.png',14.90,NULL,130,15,360,'个','ON_SALE','无印',0,1,20,'收纳,家居,防滑','2026-08-30 19:51:38','2026-08-31 21:05:06',0),(79,28,'GROC-RICE-001','大米','颗粒饱满 清香软糯','东北五常稻花香大米，粒粒饱满、蒸饭清香软糯。','https://super-zzm.oss-cn-beijing.aliyuncs.com/product/2026/3faa958cfe2f4f969476ecc019d2ec3d.png',39.90,49.88,90,10,820,'bag','ON_SALE','五常',1,0,21,'主食,米,三餐','2026-08-30 19:51:38','2026-08-31 21:05:26',0),(80,28,'GROC-OIL-001','食用油','非转基因 清淡少油烟','非转基因调和油，清淡少油烟，煎炒烹炸都合适。','https://super-zzm.oss-cn-beijing.aliyuncs.com/product/2026/c28013fde850403a884391e8d68d8aa9.png',59.90,NULL,70,10,470,'桶','ON_SALE','金龙鱼',0,0,22,'食用油,烹饪,健康','2026-08-30 19:51:38','2026-08-31 21:07:13',0),(81,28,'GROC-SOY-001','生抽','酿造酱油 提鲜上色','传统酿造生抽，鲜咸适口、上色自然，凉拌炒菜皆宜。','https://super-zzm.oss-cn-beijing.aliyuncs.com/product/2026/a9f906619f424bf7ac62016ed33d1557.png',8.90,NULL,160,15,610,'bottle','ON_SALE','海天',0,0,23,'调味,酱油,烹饪','2026-08-30 19:51:38','2026-08-31 21:07:29',0),(82,28,'GROC-SALT-001','食盐','精制加碘 日常调味','精制加碘食盐，颗粒细腻、溶解快，家常调味必备。','https://super-zzm.oss-cn-beijing.aliyuncs.com/product/2026/ca08a82d42be43bba0ff503fc579ca0a.png',2.90,NULL,300,30,530,'bag','ON_SALE','中盐',0,0,24,'调味,盐,必备','2026-08-30 19:51:38','2026-08-31 21:07:45',0),(83,28,'GROC-VINEGAR-001','醋','粮食酿造 酸香开胃','粮食酿造食醋，酸香醇厚、凉拌蘸食都开胃。','https://super-zzm.oss-cn-beijing.aliyuncs.com/product/2026/e970e31c0ba64aae84b4f92604946755.png',6.90,NULL,140,15,400,'bottle','ON_SALE','恒顺',0,0,25,'调味,醋,凉拌','2026-08-30 19:51:38','2026-08-31 21:08:28',0),(84,29,'DAIRY-MILK-001','纯牛奶','生牛乳 营养早餐','100% 生牛乳纯牛奶，富含蛋白与钙，早餐温饮皆宜。','https://super-zzm.oss-cn-beijing.aliyuncs.com/product/2026/c84d2c06a5594aaaba8eb5cf877948e7.png',19.90,24.88,180,20,1340,'box','ON_SALE','蒙牛',1,0,26,'乳制品,早餐,补钙','2026-08-30 19:51:38','2026-08-31 21:08:40',0),(85,29,'DAIRY-YOGURT-001','酸奶','浓稠畅轻 活性菌','希腊风味酸奶，浓稠醇厚、富含活性菌，饭后小食。','https://super-zzm.oss-cn-beijing.aliyuncs.com/product/2026/1ed2a8cbdae94063a68281773b271568.png',15.90,NULL,150,15,770,'杯','ON_SALE','安慕希',0,0,27,'乳制品,酸奶,益生菌','2026-08-30 19:51:38','2026-08-31 21:08:58',0),(86,29,'DAIRY-BREAD-001','面包','松软吐司 即食早餐','松软吐司面包，麦香浓郁、口感绵软，匆忙早晨随手拿。','https://super-zzm.oss-cn-beijing.aliyuncs.com/product/2026/16034ad59670496f8b7b3b91875a086d.png',8.90,NULL,120,15,690,'个','ON_SALE','桃李',0,1,28,'烘焙,早餐,吐司','2026-08-30 19:51:38','2026-08-31 21:09:16',0),(87,29,'DAIRY-CAKE-001','蛋糕','鲜奶油 节日甜点','鲜奶油水果蛋糕，绵软香甜、造型精致，生日聚会首选。','https://super-zzm.oss-cn-beijing.aliyuncs.com/product/2026/65db9734d1344ecb98fb639f6c19bb6f.png',25.90,NULL,60,10,350,'box','ON_SALE','好利来',0,0,29,'烘焙,甜点,庆祝','2026-08-30 19:51:38','2026-08-31 21:09:38',0),(88,29,'DAIRY-BUTTER-001','黄油','动物奶油 烘焙原料','动物性黄油，奶香浓郁、易涂抹，烘焙与煎烤好帮手。','https://super-zzm.oss-cn-beijing.aliyuncs.com/product/2026/31f9029f89f84262b2700f23c9bdf7e5.png',22.90,NULL,90,10,300,'box','ON_SALE','安佳',0,0,30,'乳制品,烘焙,涂抹','2026-08-30 19:51:38','2026-08-31 21:09:58',0);
/*!40000 ALTER TABLE `product` ENABLE KEYS */;
UNLOCK TABLES;

LOCK TABLES `product_sku` WRITE;
/*!40000 ALTER TABLE `product_sku` DISABLE KEYS */;
/*!40000 ALTER TABLE `product_sku` ENABLE KEYS */;
UNLOCK TABLES;

LOCK TABLES `product_image` WRITE;
/*!40000 ALTER TABLE `product_image` DISABLE KEYS */;
/*!40000 ALTER TABLE `product_image` ENABLE KEYS */;
UNLOCK TABLES;

LOCK TABLES `product_review` WRITE;
/*!40000 ALTER TABLE `product_review` DISABLE KEYS */;
/*!40000 ALTER TABLE `product_review` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

