
DROP TABLE IF EXISTS restcomm_instances;
DROP TABLE IF EXISTS phone_numbers;
DROP TABLE IF EXISTS call_records;


CREATE TABLE IF NOT EXISTS restcomm_instances (
id VARCHAR(20) NOT NULL PRIMARY KEY,
publicIpAddress VARCHAR(50),
udpInterface VARCHAR(50),
tcpInterface VARCHAR(50),
tlsInterface VARCHAR(50),
wsInterface VARCHAR(50),
dateCreated DATETIME NOT NULL,
provisionProvider VARCHAR(50)
);

CREATE TABLE IF NOT EXISTS phone_numbers (
did VARCHAR(20) NOT NULL PRIMARY KEY,
restcomm_instance VARCHAR(20) NOT NULL,
dateCreated DATETIME NOT NULL,
);

CREATE TABLE IF NOT EXISTS call_records (
did VARCHAR(20) NOT NULL PRIMARY KEY,
dateCreated DATETIME NOT NULL,
);
