# dataauthority-db
CREATE DATABASE IF NOT EXISTS `dataauthority-db`;
CREATE USER 'springuser'@'%' IDENTIFIED BY 'ThePassword';
GRANT ALL PRIVILEGES ON `dataauthority-db`.* TO 'springuser'@'%';

# validation-db
CREATE DATABASE IF NOT EXISTS `validation-db`;
CREATE USER 'validationuser'@'%' IDENTIFIED BY 'ThePassword';
GRANT ALL PRIVILEGES ON `validation-db`.* TO 'validationuser'@'%';



