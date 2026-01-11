-- MySQL dump 10.13  Distrib 9.2.0, for Win64 (x86_64)
--
-- Host: localhost    Database: tomatomall
-- ------------------------------------------------------
-- Server version	9.2.0

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
-- Table structure for table `account`
--

DROP TABLE IF EXISTS `account`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `account` (
  `id` int NOT NULL AUTO_INCREMENT,
  `avatar` varchar(255) DEFAULT NULL,
  `email` varchar(255) DEFAULT NULL,
  `location` varchar(255) DEFAULT NULL,
  `name` varchar(255) DEFAULT NULL,
  `password` varchar(255) DEFAULT NULL,
  `role` varchar(255) DEFAULT NULL,
  `telephone` varchar(255) DEFAULT NULL,
  `username` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `account`
--

LOCK TABLES `account` WRITE;
/*!40000 ALTER TABLE `account` DISABLE KEYS */;
INSERT INTO `account` VALUES (1,'https://bluewhale-mon.oss-cn-nanjing.aliyuncs.com/andy.png','','','ch','$2a$10$bu6DFXAGr0pu4aKEWFr.lOkrUfK5EIBG6842zC6bA3EkaAiUcRdoi','admin','','user'),(2,'https://bucket-231250047.oss-cn-beijing.aliyuncs.com/avart.jpeg','231250000@smail.nju.edu.cn','','ch','$2a$10$U.xDR1pgXTkZk4GTMwY1JebHTphw/3WlEqdrnxEu/mvKPE4GTWfaC','user','13000000000','custome'),(3,'','','','ch','$2a$10$fpW9ypIGWIMapcBR96i58ujpJ4k6Z1jUlgTVkwJrhvFrHWLZmedIe','user','','custome1'),(4,'https://bucket-231250047.oss-cn-beijing.aliyuncs.com/avart.jpeg','','','chh','$2a$10$hBJkuUp4QDLfmcBi4yH/M.GtQa9MvzKeoFmbJtwtaTQG7I7zCYVDG','user','14000000000','custome4');
/*!40000 ALTER TABLE `account` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `advertisements`
--

DROP TABLE IF EXISTS `advertisements`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `advertisements` (
  `id` int NOT NULL,
  `content` varchar(500) NOT NULL,
  `image_url` varchar(500) NOT NULL,
  `product_id` int NOT NULL,
  `title` varchar(50) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `advertisements`
--

LOCK TABLES `advertisements` WRITE;
/*!40000 ALTER TABLE `advertisements` DISABLE KEYS */;
INSERT INTO `advertisements` VALUES (12,'马克思主义基本原理','https://bucket-231250047.oss-cn-beijing.aliyuncs.com/马克思.avif',48,'马克思'),(13,'明朝那些事儿\n','https://bucket-231250047.oss-cn-beijing.aliyuncs.com/明朝那些事儿.webp',47,'明朝那些事儿');
/*!40000 ALTER TABLE `advertisements` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `carts`
--

DROP TABLE IF EXISTS `carts`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `carts` (
  `cart_item_id` int NOT NULL AUTO_INCREMENT,
  `product_id` int DEFAULT NULL,
  `quantity` int DEFAULT NULL,
  `user_id` int DEFAULT NULL,
  PRIMARY KEY (`cart_item_id`)
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `carts`
--

LOCK TABLES `carts` WRITE;
/*!40000 ALTER TABLE `carts` DISABLE KEYS */;
INSERT INTO `carts` VALUES (4,22,1,1),(9,23,1,1),(10,50,2,4);
/*!40000 ALTER TABLE `carts` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `carts_orders_relation`
--

DROP TABLE IF EXISTS `carts_orders_relation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `carts_orders_relation` (
  `id` int NOT NULL AUTO_INCREMENT,
  `cartitem_id` int DEFAULT NULL,
  `order_id` int DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `carts_orders_relation`
--

LOCK TABLES `carts_orders_relation` WRITE;
/*!40000 ALTER TABLE `carts_orders_relation` DISABLE KEYS */;
INSERT INTO `carts_orders_relation` VALUES (1,4,1),(2,9,1);
/*!40000 ALTER TABLE `carts_orders_relation` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `comment`
--

DROP TABLE IF EXISTS `comment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `comment` (
  `id` int NOT NULL AUTO_INCREMENT,
  `comment_str` varchar(255) DEFAULT NULL,
  `create_time` datetime(6) DEFAULT NULL,
  `product_id` int DEFAULT NULL,
  `username` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `comment`
--

LOCK TABLES `comment` WRITE;
/*!40000 ALTER TABLE `comment` DISABLE KEYS */;
INSERT INTO `comment` VALUES (1,'为什么在商品详情处就可以发布评论呢？','2025-12-09 03:13:16.083903',23,'user');
/*!40000 ALTER TABLE `comment` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `hibernate_sequence`
--

DROP TABLE IF EXISTS `hibernate_sequence`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `hibernate_sequence` (
  `next_val` bigint DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `hibernate_sequence`
--

LOCK TABLES `hibernate_sequence` WRITE;
/*!40000 ALTER TABLE `hibernate_sequence` DISABLE KEYS */;
INSERT INTO `hibernate_sequence` VALUES (14),(14);
/*!40000 ALTER TABLE `hibernate_sequence` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `orders`
--

DROP TABLE IF EXISTS `orders`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `orders` (
  `order_id` int NOT NULL AUTO_INCREMENT,
  `create_time` datetime(6) DEFAULT NULL,
  `payment_method` varchar(50) NOT NULL,
  `status` varchar(20) NOT NULL,
  `total_amount` decimal(10,2) NOT NULL,
  `user_id` int NOT NULL,
  PRIMARY KEY (`order_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `orders`
--

LOCK TABLES `orders` WRITE;
/*!40000 ALTER TABLE `orders` DISABLE KEYS */;
INSERT INTO `orders` VALUES (1,'2025-12-09 09:16:11.090000','ALIPAY','PENDING',133.50,1);
/*!40000 ALTER TABLE `orders` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `products`
--

DROP TABLE IF EXISTS `products`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `products` (
  `id` int NOT NULL AUTO_INCREMENT,
  `cover` varchar(500) DEFAULT NULL,
  `create_time` datetime(6) DEFAULT NULL,
  `description` varchar(255) DEFAULT NULL,
  `detail` varchar(500) DEFAULT NULL,
  `price` decimal(10,2) NOT NULL,
  `rate` double NOT NULL,
  `title` varchar(255) NOT NULL,
  `tag` varchar(255) NOT NULL,
  `seller_id` int DEFAULT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'available',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=51 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `products`
--

LOCK TABLES `products` WRITE;
/*!40000 ALTER TABLE `products` DISABLE KEYS */;
INSERT INTO `products` VALUES (47,'https://bucket-231250047.oss-cn-beijing.aliyuncs.com/明朝那些事儿.webp',NULL,'','',69.00,9,'明朝那些事儿','history',4,'available'),(48,'https://bucket-231250047.oss-cn-beijing.aliyuncs.com/马克思.avif',NULL,'马克思主义基本原理','',8.00,8,'马克思','education',1,'available'),(49,'https://bucket-231250047.oss-cn-beijing.aliyuncs.com/毛泽东.jpg',NULL,'毛泽东思想中国特色社会主义理论体系','',10.00,9,'毛泽东','education',1,'available'),(50,'https://bucket-231250047.oss-cn-beijing.aliyuncs.com/食南之鬼.jpg',NULL,'','',23.00,7,'食南之鬼','literature',1,'available');
/*!40000 ALTER TABLE `products` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `specifications`
--

DROP TABLE IF EXISTS `specifications`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `specifications` (
  `id` int NOT NULL AUTO_INCREMENT,
  `item` varchar(50) NOT NULL,
  `product_id` int NOT NULL,
  `value` varchar(255) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=57 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `specifications`
--

LOCK TABLES `specifications` WRITE;
/*!40000 ALTER TABLE `specifications` DISABLE KEYS */;
INSERT INTO `specifications` VALUES (1,'作者',102,'Robert C. Martin'),(2,'副标题',102,'程序员的职业素养'),(3,'ISBN',102,'9787121316633'),(4,'装帧',102,'精装'),(5,'页数',102,'388'),(6,'出版社',102,'人民邮电出版社'),(7,'出版日期',102,'2018-01-01'),(8,'作者',21,'Robert C. Martin'),(9,'副标题',21,'程序员的职业素养'),(10,'ISBN',21,'9787121316633'),(11,'装帧',21,'精装'),(12,'页数',21,'388'),(13,'出版社',21,'人民邮电出版社'),(14,'出版日期',21,'2018-01-01'),(15,'作者',22,'Robert C. Martin'),(16,'副标题',22,'程序员的职业素养'),(17,'ISBN',22,'9787121316633'),(18,'装帧',22,'精装'),(19,'页数',22,'388'),(20,'出版社',22,'人民邮电出版社'),(21,'出版日期',22,'2018-01-01'),(22,'作者',23,'周志明'),(23,'副标题',23,'JVM高级特性与最佳实践'),(24,'ISBN',23,'9787111421900'),(25,'装帧',23,'平装'),(26,'页数',23,'540'),(27,'出版社',23,'机械工业出版社'),(28,'出版日期',23,'2013-09-01'),(29,'作者',24,'1'),(30,'出版社',24,'2'),(31,'时间',24,'3'),(32,'作者',25,'1'),(33,'出版社',25,'2'),(34,'时间',25,'3'),(35,'作者',26,'1'),(36,'出版社',26,'3'),(37,'时间',26,'5'),(38,'版型',27,'2023'),(39,'',27,''),(40,'版型',28,'2013'),(41,'版号',29,'2013'),(42,'版号',30,'2013'),(43,'33',31,'44'),(44,'33',32,'44'),(45,'',33,''),(46,'33',34,'44'),(47,'33',35,'44'),(48,'33',36,'44'),(49,'33',37,'44'),(50,'33',38,'44'),(51,'33',39,'44'),(52,'33',40,'44'),(53,'',42,''),(54,'作者',47,'当年明月'),(55,'版号',48,'2023'),(56,'版号',49,'2013');
/*!40000 ALTER TABLE `specifications` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `stockpiles`
--

DROP TABLE IF EXISTS `stockpiles`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `stockpiles` (
  `id` int NOT NULL AUTO_INCREMENT,
  `amount` int NOT NULL,
  `frozen` int NOT NULL,
  `product_id` int NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKgfj7xw0trgrvvisavbabl3nd8` (`product_id`),
  CONSTRAINT `FKgfj7xw0trgrvvisavbabl3nd8` FOREIGN KEY (`product_id`) REFERENCES `products` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=30 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `stockpiles`
--

LOCK TABLES `stockpiles` WRITE;
/*!40000 ALTER TABLE `stockpiles` DISABLE KEYS */;
INSERT INTO `stockpiles` VALUES (26,400,0,47),(27,400,0,48),(28,400,0,49),(29,400,0,50);
/*!40000 ALTER TABLE `stockpiles` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2025-12-11  9:42:07