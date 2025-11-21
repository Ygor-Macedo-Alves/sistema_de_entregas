-- =====================================================
-- CONNECT DELIVERY - BANCO DE DADOS MySQL
-- =====================================================

-- Criar o banco de dados
DROP DATABASE IF EXISTS connect_delivery;
CREATE DATABASE connect_delivery CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE connect_delivery;

-- =====================================================
-- TABELA: CLIENTES
-- =====================================================
CREATE TABLE clientes (
    id_cliente INT AUTO_INCREMENT PRIMARY KEY,
    nome_completo VARCHAR(150) NOT NULL,
    cpf VARCHAR(14) NOT NULL UNIQUE,
    endereco VARCHAR(255) NOT NULL,
    telefone VARCHAR(20) NOT NULL,
    email VARCHAR(100) NOT NULL UNIQUE,
    senha VARCHAR(255) NOT NULL,
    data_cadastro TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    ativo BOOLEAN DEFAULT TRUE,
    INDEX idx_email (email),
    INDEX idx_cpf (cpf)
) ENGINE=InnoDB;

-- =====================================================
-- TABELA: LOJAS/RESTAURANTES
-- =====================================================
CREATE TABLE lojas (
    id_loja INT AUTO_INCREMENT PRIMARY KEY,
    nome_restaurante VARCHAR(150) NOT NULL,
    cnpj VARCHAR(18) NOT NULL UNIQUE,
    endereco VARCHAR(255) NOT NULL,
    horario_funcionamento VARCHAR(100),
    email_gerente VARCHAR(100) NOT NULL UNIQUE,
    senha VARCHAR(255) NOT NULL,
    data_cadastro TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    ativo BOOLEAN DEFAULT TRUE,
    INDEX idx_email (email_gerente),
    INDEX idx_cnpj (cnpj)
) ENGINE=InnoDB;

-- =====================================================
-- TABELA: ENTREGADORES
-- =====================================================
CREATE TABLE entregadores (
    id_entregador INT AUTO_INCREMENT PRIMARY KEY,
    nome_completo VARCHAR(150) NOT NULL,
    rg VARCHAR(20) NOT NULL,
    cpf_documento VARCHAR(14) NOT NULL UNIQUE,
    cnh VARCHAR(20) NOT NULL UNIQUE,
    tipo_veiculo VARCHAR(50) NOT NULL,
    placa VARCHAR(10) NOT NULL,
    email VARCHAR(100) NOT NULL UNIQUE,
    senha VARCHAR(255) NOT NULL,
    disponibilidade ENUM('ONLINE', 'OFFLINE') DEFAULT 'OFFLINE',
    data_cadastro TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    ativo BOOLEAN DEFAULT TRUE,
    INDEX idx_email (email),
    INDEX idx_disponibilidade (disponibilidade)
) ENGINE=InnoDB;

-- =====================================================
-- TABELA: PRODUTOS (CARDÁPIO)
-- =====================================================
CREATE TABLE produtos (
    id_produto INT AUTO_INCREMENT PRIMARY KEY,
    id_loja INT NOT NULL,
    nome VARCHAR(150) NOT NULL,
    preco DECIMAL(10, 2) NOT NULL,
    descricao TEXT,
    disponivel BOOLEAN DEFAULT TRUE,
    data_cadastro TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (id_loja) REFERENCES lojas(id_loja) ON DELETE CASCADE,
    INDEX idx_loja (id_loja),
    INDEX idx_disponivel (disponivel)
) ENGINE=InnoDB;

-- =====================================================
-- TABELA: PEDIDOS
-- =====================================================
CREATE TABLE pedidos (
    id_pedido INT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    id_cliente INT NOT NULL,
    id_loja INT NOT NULL,
    id_entregador INT NULL,
    status ENUM(
        'PEDIDO_RECEBIDO', 
        'EM_PREPARO', 
        'PRONTO_COLETA', 
        'SAIU_ENTREGA', 
        'ENTREGUE', 
        'CANCELADO'
    ) DEFAULT 'PEDIDO_RECEBIDO',
    valor_total DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    hora_pedido TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    hora_preparo TIMESTAMP NULL,
    hora_pronto TIMESTAMP NULL,
    hora_saiu_entrega TIMESTAMP NULL,
    hora_entregue TIMESTAMP NULL,
    codigo_confirmacao VARCHAR(10) NULL,
    observacoes TEXT,
    FOREIGN KEY (id_cliente) REFERENCES clientes(id_cliente),
    FOREIGN KEY (id_loja) REFERENCES lojas(id_loja),
    FOREIGN KEY (id_entregador) REFERENCES entregadores(id_entregador) ON DELETE SET NULL,
    INDEX idx_status (status),
    INDEX idx_cliente (id_cliente),
    INDEX idx_loja (id_loja),
    INDEX idx_entregador (id_entregador),
    INDEX idx_hora_pedido (hora_pedido)
) ENGINE=InnoDB;

-- =====================================================
-- TABELA: ITENS DO PEDIDO
-- =====================================================
CREATE TABLE itens_pedido (
    id_item INT AUTO_INCREMENT PRIMARY KEY,
    id_pedido INT NOT NULL,
    id_produto INT NOT NULL,
    quantidade INT NOT NULL DEFAULT 1,
    preco_unitario DECIMAL(10, 2) NOT NULL,
    subtotal DECIMAL(10, 2) NOT NULL,
    FOREIGN KEY (id_pedido) REFERENCES pedidos(id_pedido) ON DELETE CASCADE,
    FOREIGN KEY (id_produto) REFERENCES produtos(id_produto),
    INDEX idx_pedido (id_pedido),
    INDEX idx_produto (id_produto)
) ENGINE=InnoDB;

-- =====================================================
-- TABELA: HISTÓRICO DE STATUS
-- =====================================================
CREATE TABLE historico_status (
    id_historico INT AUTO_INCREMENT PRIMARY KEY,
    id_pedido INT NOT NULL,
    status_anterior ENUM(
        'PEDIDO_RECEBIDO', 
        'EM_PREPARO', 
        'PRONTO_COLETA', 
        'SAIU_ENTREGA', 
        'ENTREGUE', 
        'CANCELADO'
    ),
    status_novo ENUM(
        'PEDIDO_RECEBIDO', 
        'EM_PREPARO', 
        'PRONTO_COLETA', 
        'SAIU_ENTREGA', 
        'ENTREGUE', 
        'CANCELADO'
    ) NOT NULL,
    data_mudanca TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    observacao VARCHAR(255),
    FOREIGN KEY (id_pedido) REFERENCES pedidos(id_pedido) ON DELETE CASCADE,
    INDEX idx_pedido (id_pedido),
    INDEX idx_data (data_mudanca)
) ENGINE=InnoDB;

-- =====================================================
-- VIEWS ÚTEIS
-- =====================================================

-- View: Pedidos com informações completas
CREATE VIEW vw_pedidos_completos AS
SELECT 
    p.id_pedido,
    p.uuid,
    p.status,
    p.valor_total,
    p.hora_pedido,
    p.hora_entregue,
    c.nome_completo AS cliente_nome,
    c.endereco AS cliente_endereco,
    c.telefone AS cliente_telefone,
    l.nome_restaurante AS loja_nome,
    l.endereco AS loja_endereco,
    e.nome_completo AS entregador_nome,
    e.tipo_veiculo AS entregador_veiculo,
    e.placa AS entregador_placa,
    TIMESTAMPDIFF(MINUTE, p.hora_pedido, COALESCE(p.hora_entregue, NOW())) AS tempo_decorrido_min
FROM pedidos p
INNER JOIN clientes c ON p.id_cliente = c.id_cliente
INNER JOIN lojas l ON p.id_loja = l.id_loja
LEFT JOIN entregadores e ON p.id_entregador = e.id_entregador;

-- View: Produtos por loja
CREATE VIEW vw_cardapio_lojas AS
SELECT 
    l.id_loja,
    l.nome_restaurante,
    p.id_produto,
    p.nome AS produto_nome,
    p.preco,
    p.descricao,
    p.disponivel
FROM lojas l
INNER JOIN produtos p ON l.id_loja = p.id_loja
WHERE l.ativo = TRUE AND p.disponivel = TRUE;

-- View: Entregadores disponíveis
CREATE VIEW vw_entregadores_disponiveis AS
SELECT 
    e.id_entregador,
    e.nome_completo,
    e.tipo_veiculo,
    e.placa,
    e.email,
    COUNT(p.id_pedido) AS entregas_em_andamento
FROM entregadores e
LEFT JOIN pedidos p ON e.id_entregador = p.id_entregador 
    AND p.status IN ('SAIU_ENTREGA')
WHERE e.disponibilidade = 'ONLINE' AND e.ativo = TRUE
GROUP BY e.id_entregador, e.nome_completo, e.tipo_veiculo, e.placa, e.email;

-- =====================================================
-- TRIGGERS
-- =====================================================

-- Trigger: Atualizar valor total do pedido
DELIMITER //
CREATE TRIGGER trg_atualizar_valor_pedido
AFTER INSERT ON itens_pedido
FOR EACH ROW
BEGIN
    UPDATE pedidos 
    SET valor_total = (
        SELECT SUM(subtotal) 
        FROM itens_pedido 
        WHERE id_pedido = NEW.id_pedido
    )
    WHERE id_pedido = NEW.id_pedido;
END//
DELIMITER ;

-- Trigger: Registrar mudança de status no histórico
DELIMITER //
CREATE TRIGGER trg_registrar_historico_status
AFTER UPDATE ON pedidos
FOR EACH ROW
BEGIN
    IF OLD.status != NEW.status THEN
        INSERT INTO historico_status (id_pedido, status_anterior, status_novo)
        VALUES (NEW.id_pedido, OLD.status, NEW.status);
    END IF;
END//
DELIMITER ;

-- Trigger: Atualizar timestamps ANTES da atualização
DELIMITER //
CREATE TRIGGER trg_atualizar_timestamps_status
BEFORE UPDATE ON pedidos
FOR EACH ROW
BEGIN
    -- Atualizar timestamps baseado no novo status
    IF OLD.status != NEW.status THEN
        IF NEW.status = 'EM_PREPARO' AND NEW.hora_preparo IS NULL THEN
            SET NEW.hora_preparo = NOW();
        END IF;
        
        IF NEW.status = 'PRONTO_COLETA' AND NEW.hora_pronto IS NULL THEN
            SET NEW.hora_pronto = NOW();
        END IF;
        
        IF NEW.status = 'SAIU_ENTREGA' AND NEW.hora_saiu_entrega IS NULL THEN
            SET NEW.hora_saiu_entrega = NOW();
        END IF;
        
        IF NEW.status = 'ENTREGUE' AND NEW.hora_entregue IS NULL THEN
            SET NEW.hora_entregue = NOW();
        END IF;
    END IF;
END//
DELIMITER ;

-- =====================================================
-- STORED PROCEDURES
-- =====================================================

-- Procedure: Criar novo pedido
DELIMITER //
CREATE PROCEDURE sp_criar_pedido(
    IN p_id_cliente INT,
    IN p_id_loja INT,
    IN p_observacoes TEXT,
    OUT p_id_pedido INT,
    OUT p_uuid VARCHAR(36)
)
BEGIN
    SET p_uuid = UUID();
    
    INSERT INTO pedidos (uuid, id_cliente, id_loja, observacoes)
    VALUES (p_uuid, p_id_cliente, p_id_loja, p_observacoes);
    
    SET p_id_pedido = LAST_INSERT_ID();
END//
DELIMITER ;

-- Procedure: Adicionar item ao pedido
DELIMITER //
CREATE PROCEDURE sp_adicionar_item_pedido(
    IN p_id_pedido INT,
    IN p_id_produto INT,
    IN p_quantidade INT
)
BEGIN
    DECLARE v_preco DECIMAL(10,2);
    DECLARE v_subtotal DECIMAL(10,2);
    
    SELECT preco INTO v_preco FROM produtos WHERE id_produto = p_id_produto;
    SET v_subtotal = v_preco * p_quantidade;
    
    INSERT INTO itens_pedido (id_pedido, id_produto, quantidade, preco_unitario, subtotal)
    VALUES (p_id_pedido, p_id_produto, p_quantidade, v_preco, v_subtotal);
END//
DELIMITER ;

-- Procedure: Atualizar status do pedido
DELIMITER //
CREATE PROCEDURE sp_atualizar_status_pedido(
    IN p_id_pedido INT,
    IN p_novo_status VARCHAR(20),
    IN p_observacao VARCHAR(255)
)
BEGIN
    UPDATE pedidos 
    SET status = p_novo_status 
    WHERE id_pedido = p_id_pedido;
    
    -- O trigger irá registrar no histórico automaticamente
END//
DELIMITER ;

-- =====================================================
-- DADOS DE EXEMPLO
-- =====================================================


-- Atualizar status dos pedidos de exemplo
-- Como as variáveis @pedido1 e @pedido2 podem não funcionar em todos os contextos,
-- vamos usar os IDs dos últimos pedidos inseridos
UPDATE pedidos SET status = 'EM_PREPARO', id_entregador = 1 
WHERE id_pedido = (SELECT MAX(id_pedido) FROM (SELECT id_pedido FROM pedidos) AS temp) - 1;

UPDATE pedidos SET status = 'PRONTO_COLETA', id_entregador = 2 
WHERE id_pedido = (SELECT MAX(id_pedido) FROM (SELECT id_pedido FROM pedidos) AS temp);

select * from itens_pedido;