DROP DATABASE duocount;
CREATE DATABASE duocount;
USE duocount;

CREATE TABLE users (
                       id INT AUTO_INCREMENT PRIMARY KEY,
                       name VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE wallets (
                         id INT AUTO_INCREMENT PRIMARY KEY,
                         name VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE expenses (
                          id INT AUTO_INCREMENT PRIMARY KEY,
                          wallet_id INT NOT NULL,
                          payer_id INT NOT NULL,
                          description VARCHAR(255) NOT NULL,
                          amount DECIMAL(10, 2) NOT NULL,
                          FOREIGN KEY (wallet_id) REFERENCES wallets(id),
                          FOREIGN KEY (payer_id) REFERENCES users(id)
);

CREATE TABLE expense_participants (
                                      id INT AUTO_INCREMENT PRIMARY KEY,
                                      expense_id INT NOT NULL,
                                      participant_id INT NOT NULL,
                                      share DECIMAL(10, 2) NOT NULL,
                                      FOREIGN KEY (expense_id) REFERENCES expenses(id),
                                      FOREIGN KEY (participant_id) REFERENCES users(id)
);
