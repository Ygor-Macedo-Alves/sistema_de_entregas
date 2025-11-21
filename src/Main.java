import java.sql.*;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.Scanner;
import java.util.stream.Collectors;

// ===========================================
// CLASSE DE CONEXÃO COM O BANCO DE DADOS
// ===========================================
// banco de dados A3
class DatabaseConnection {
    private static final String URL = "jdbc:mysql://localhost:3306/connect_delivery?useSSL=false&serverTimezone=UTC";
    private static final String USER = "root"; // Altere para seu usuário MySQL
    private static final String PASSWORD = "1603"; // Altere para sua senha MySQL

    public static Connection getConnection() throws SQLException {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            return DriverManager.getConnection(URL, USER, PASSWORD);
        } catch (ClassNotFoundException e) {
            throw new SQLException("Driver MySQL não encontrado!", e);
        }
    }
}

// ===========================================
// ENUMS
// ===========================================

enum StatusPedido {
    PEDIDO_RECEBIDO("Pedido Recebido"), EM_PREPARO("Em Preparo"),
    PRONTO_COLETA("Pronto para Coleta"), SAIU_ENTREGA("Saiu para Entrega"),
    ENTREGUE("Entregue"), CANCELADO("Cancelado");
    private final String descricao;
    StatusPedido(String descricao) { this.descricao = descricao; }
    @Override public String toString() { return descricao; }
}

enum DisponibilidadeEntregador {
    ONLINE("Online"), OFFLINE("Offline");
    private final String descricao;
    DisponibilidadeEntregador(String descricao) { this.descricao = descricao; }
    @Override public String toString() { return descricao; }
}

// ===========================================
// CLASSES BASE E ENTIDADES
// ===========================================

abstract class Usuario {
    protected int id;
    protected String email;
    protected String senha;
    public Usuario(int id, String email, String senha) {
        this.id = id;
        this.email = email;
        this.senha = senha;
    }
    public String getEmail() { return email; }
    public int getId() { return id; }
}

class Cliente extends Usuario {
    private String nomeCompleto;
    private String cpf;
    private String endereco;
    private String telefone;

    public Cliente(int id, String nomeCompleto, String cpf, String endereco, String telefone, String email, String senha) {
        super(id, email, senha);
        this.nomeCompleto = nomeCompleto;
        this.cpf = cpf;
        this.endereco = endereco;
        this.telefone = telefone;
    }
    public String getNomeCompleto() { return nomeCompleto; }
    public String getEndereco() { return endereco; }
}

class Entregador extends Usuario {
    private String nomeCompleto;
    private String rg;
    private String cpfDocumento;
    private String cnh;
    private String tipoVeiculo;
    private String placa;
    private DisponibilidadeEntregador disponibilidade = DisponibilidadeEntregador.OFFLINE;

    public Entregador(int id, String nomeCompleto, String rg, String cpfDocumento, String cnh, String tipoVeiculo, String placa, String email, String senha, String disponibilidade) {
        super(id, email, senha);
        this.nomeCompleto = nomeCompleto;
        this.rg = rg;
        this.cpfDocumento = cpfDocumento;
        this.cnh = cnh;
        this.tipoVeiculo = tipoVeiculo;
        this.placa = placa;
        this.disponibilidade = DisponibilidadeEntregador.valueOf(disponibilidade);
    }
    public String getNomeCompleto() { return nomeCompleto; }
    public void setDisponibilidade(DisponibilidadeEntregador disponibilidade) {
        this.disponibilidade = disponibilidade;
        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = "UPDATE entregadores SET disponibilidade = ? WHERE id_entregador = ?";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setString(1, disponibilidade.name());
            stmt.setInt(2, this.id);
            stmt.executeUpdate();
            System.out.println("Entregador " + this.getNomeCompleto() + " está agora: " + disponibilidade);
        } catch (SQLException e) {
            System.err.println("Erro ao atualizar disponibilidade: " + e.getMessage());
        }
    }
    public DisponibilidadeEntregador getDisponibilidade() { return disponibilidade; }
}

class Produto {
    private int id;
    private String nome;
    private double preco;
    private String descricao;

    public Produto(int id, String nome, double preco, String descricao) {
        this.id = id;
        this.nome = nome;
        this.preco = preco;
        this.descricao = descricao;
    }
    public int getId() { return id; }
    public String getNome() { return nome; }
    public double getPreco() { return preco; }
    @Override
    public String toString() { return String.format("%s (R$ %.2f)", nome, preco); }
}

class Loja extends Usuario {
    private String nomeRestaurante;
    private String cnpj;
    private String endereco;
    private String horarioFuncionamento;

    public Loja(int id, String nomeRestaurante, String cnpj, String endereco, String horarioFuncionamento, String emailGerente, String senha) {
        super(id, emailGerente, senha);
        this.nomeRestaurante = nomeRestaurante;
        this.cnpj = cnpj;
        this.endereco = endereco;
        this.horarioFuncionamento = horarioFuncionamento;
    }
    public String getNomeRestaurante() { return nomeRestaurante; }

    public List<Produto> getCardapio() {
        List<Produto> produtos = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = "SELECT * FROM produtos WHERE id_loja = ? AND disponivel = TRUE";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setInt(1, this.id);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                produtos.add(new Produto(
                        rs.getInt("id_produto"),
                        rs.getString("nome"),
                        rs.getDouble("preco"),
                        rs.getString("descricao")
                ));
            }
        } catch (SQLException e) {
            System.err.println("Erro ao buscar cardápio: " + e.getMessage());
        }
        return produtos;
    }

    public void confirmarPedido(int idPedido) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = "UPDATE pedidos SET status = 'EM_PREPARO' WHERE id_pedido = ? AND status = 'PEDIDO_RECEBIDO'";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setInt(1, idPedido);
            int rows = stmt.executeUpdate();
            if (rows > 0) {
                System.out.printf("  --> Loja %s confirmou e começou o preparo do Pedido #%d.\n", nomeRestaurante, idPedido);
            }
        } catch (SQLException e) {
            System.err.println("Erro ao confirmar pedido: " + e.getMessage());
        }
    }

    public void prepararParaColeta(int idPedido) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = "UPDATE pedidos SET status = 'PRONTO_COLETA' WHERE id_pedido = ? AND status = 'EM_PREPARO'";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setInt(1, idPedido);
            int rows = stmt.executeUpdate();
            if (rows > 0) {
                System.out.printf("  --> Loja %s finalizou o preparo do Pedido #%d. Pronto para coleta!\n", nomeRestaurante, idPedido);
            }
        } catch (SQLException e) {
            System.err.println("Erro ao preparar para coleta: " + e.getMessage());
        }
    }
}

class Pedido {
    private int id;
    private String uuid;
    private Cliente cliente;
    private Loja loja;
    private LocalDateTime horaPedido;
    private StatusPedido status;
    private Entregador entregador;
    private double valorTotal;

    public Pedido(int id, String uuid, Cliente cliente, Loja loja, LocalDateTime horaPedido, String status, double valorTotal) {
        this.id = id;
        this.uuid = uuid;
        this.cliente = cliente;
        this.loja = loja;
        this.horaPedido = horaPedido;
        this.status = StatusPedido.valueOf(status);
        this.valorTotal = valorTotal;
    }

    public int getId() { return id; }
    public String getUuid() { return uuid; }
    public String getIdCurto() { return uuid.substring(0, 8); }
    public LocalDateTime getHoraPedido() { return horaPedido; }
    public StatusPedido getStatus() { return status; }
    public Loja getLoja() { return loja; }
    public Cliente getCliente() { return cliente; }
    public void setStatus(StatusPedido status) { this.status = status; }
    public void setEntregador(Entregador entregador) { this.entregador = entregador; }
    public Entregador getEntregador() { return entregador; }
    public double getValorTotal() { return valorTotal; }

    @Override
    public String toString() {
        return String.format("Pedido #%d | Loja: %s | Cliente: %s | Total: R$ %.2f | Status: %s | Hora: %s",
                id, loja.getNomeRestaurante(), cliente.getNomeCompleto(), valorTotal, status, horaPedido.toLocalTime());
    }
}

// ===========================================
// GERENCIADOR DE PEDIDOS
// ===========================================

class GerenciadorPedidos {
    private final Duration PRAZO_ENTREGA = Duration.ofMinutes(45);

    public int criarPedido(Cliente cliente, Loja loja, String observacoes) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            CallableStatement stmt = conn.prepareCall("{CALL sp_criar_pedido(?, ?, ?, ?, ?)}");
            stmt.setInt(1, cliente.getId());
            stmt.setInt(2, loja.getId());
            stmt.setString(3, observacoes);
            stmt.registerOutParameter(4, Types.INTEGER);
            stmt.registerOutParameter(5, Types.VARCHAR);
            stmt.execute();

            int idPedido = stmt.getInt(4);
            System.out.printf("\n[Sistema] Novo Pedido #%d criado com sucesso.\n", idPedido);
            return idPedido;
        } catch (SQLException e) {
            System.err.println("Erro ao criar pedido: " + e.getMessage());
            return -1;
        }
    }

    public void adicionarItemPedido(int idPedido, int idProduto, int quantidade) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            CallableStatement stmt = conn.prepareCall("{CALL sp_adicionar_item_pedido(?, ?, ?)}");
            stmt.setInt(1, idPedido);
            stmt.setInt(2, idProduto);
            stmt.setInt(3, quantidade);
            stmt.execute();
        } catch (SQLException e) {
            System.err.println("Erro ao adicionar item: " + e.getMessage());
        }
    }

    public List<Pedido> getPedidosEmAndamentoPorLoja(Loja loja) {
        List<Pedido> pedidos = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = "SELECT p.*, c.nome_completo, c.cpf, c.endereco, c.telefone, c.email " +
                    "FROM pedidos p " +
                    "JOIN clientes c ON p.id_cliente = c.id_cliente " +
                    "WHERE p.id_loja = ? AND p.status NOT IN ('ENTREGUE', 'CANCELADO') " +
                    "ORDER BY p.hora_pedido";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setInt(1, loja.getId());
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                Cliente cliente = new Cliente(
                        rs.getInt("id_cliente"),
                        rs.getString("nome_completo"),
                        rs.getString("cpf"),
                        rs.getString("endereco"),
                        rs.getString("telefone"),
                        rs.getString("email"),
                        ""
                );

                pedidos.add(new Pedido(
                        rs.getInt("id_pedido"),
                        rs.getString("uuid"),
                        cliente,
                        loja,
                        rs.getTimestamp("hora_pedido").toLocalDateTime(),
                        rs.getString("status"),
                        rs.getDouble("valor_total")
                ));
            }
        } catch (SQLException e) {
            System.err.println("Erro ao buscar pedidos: " + e.getMessage());
        }
        return pedidos;
    }

    public void visualizarPedidosEmAndamentoParaLoja(Loja loja) {
        System.out.printf("\n--- Pedidos Em Andamento para a Loja: %s ---\n", loja.getNomeRestaurante());
        List<Pedido> pedidos = getPedidosEmAndamentoPorLoja(loja);

        if (pedidos.isEmpty()) {
            System.out.println("Nenhum pedido em andamento no momento.");
            return;
        }

        for (Pedido pedido : pedidos) {
            String statusPrazo = verificarPrazo(pedido);
            System.out.printf("[%s] Pedido #%d | Cliente: %s | Status Atual: %s | Tempo Decorrido: %d min\n",
                    statusPrazo, pedido.getId(), pedido.getCliente().getNomeCompleto(), pedido.getStatus(),
                    Duration.between(pedido.getHoraPedido(), LocalDateTime.now()).toMinutes());
        }
        System.out.println("----------------------------------------------\n");
    }

    public String verificarPrazo(Pedido pedido) {
        if (pedido.getStatus() == StatusPedido.ENTREGUE || pedido.getStatus() == StatusPedido.CANCELADO) {
            return "Finalizado";
        }
        Duration tempoDecorrido = Duration.between(pedido.getHoraPedido(), LocalDateTime.now());
        return tempoDecorrido.compareTo(PRAZO_ENTREGA) > 0 ? "ATRASADO" : "Dentro do Prazo";
    }

    public void finalizarEntrega(int idPedido, String codigoConfirmacao) {
        if ("1234".equals(codigoConfirmacao)) {
            try (Connection conn = DatabaseConnection.getConnection()) {
                String sql = "UPDATE pedidos SET status = 'ENTREGUE' WHERE id_pedido = ? AND status = 'SAIU_ENTREGA'";
                PreparedStatement stmt = conn.prepareStatement(sql);
                stmt.setInt(1, idPedido);
                int rows = stmt.executeUpdate();

                if (rows > 0) {
                    System.out.printf("  --> Entrega do Pedido #%d finalizada com sucesso! (Status: ENTREGUE)\n", idPedido);
                } else {
                    System.err.println("  --> Erro: Pedido não está no status 'Saiu para Entrega'.");
                }
            } catch (SQLException e) {
                System.err.println("Erro ao finalizar entrega: " + e.getMessage());
            }
        } else {
            System.err.println("  --> Erro: Código de confirmação inválido. Entrega não finalizada.");
        }
    }

    public void relatorioHistoricoEntregues() {
        System.out.println("\n--- 6. RELATÓRIO DE PEDIDOS ENTREGUES (HISTÓRICO) ---");
        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = "SELECT * FROM vw_pedidos_completos WHERE status = 'ENTREGUE' ORDER BY hora_entregue DESC";
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(sql);

            boolean temPedidos = false;
            while (rs.next()) {
                temPedidos = true;
                System.out.printf("Pedido #%d | Loja: %s | Cliente: %s | Total: R$ %.2f | Entregue em: %s\n",
                        rs.getInt("id_pedido"),
                        rs.getString("loja_nome"),
                        rs.getString("cliente_nome"),
                        rs.getDouble("valor_total"),
                        rs.getTimestamp("hora_entregue")
                );
            }

            if (!temPedidos) {
                System.out.println("Nenhum pedido entregue ainda.");
            }
        } catch (SQLException e) {
            System.err.println("Erro ao gerar relatório: " + e.getMessage());
        }
        System.out.println("---------------------------------------------------\n");
    }

    public Pedido getPedidoPorId(int idPedido, Loja loja) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = "SELECT p.*, c.nome_completo, c.cpf, c.endereco, c.telefone, c.email " +
                    "FROM pedidos p " +
                    "JOIN clientes c ON p.id_cliente = c.id_cliente " +
                    "WHERE p.id_pedido = ? AND p.id_loja = ?";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setInt(1, idPedido);
            stmt.setInt(2, loja.getId());
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                Cliente cliente = new Cliente(
                        rs.getInt("id_cliente"),
                        rs.getString("nome_completo"),
                        rs.getString("cpf"),
                        rs.getString("endereco"),
                        rs.getString("telefone"),
                        rs.getString("email"),
                        ""
                );

                return new Pedido(
                        rs.getInt("id_pedido"),
                        rs.getString("uuid"),
                        cliente,
                        loja,
                        rs.getTimestamp("hora_pedido").toLocalDateTime(),
                        rs.getString("status"),
                        rs.getDouble("valor_total")
                );
            }
        } catch (SQLException e) {
            System.err.println("Erro ao buscar pedido: " + e.getMessage());
        }
        return null;
    }
}

// ===========================================
// CLASSE PRINCIPAL
// ===========================================

public class Main {

    private static String lerString(Scanner scanner, String prompt) {
        System.out.print(prompt);
        return scanner.nextLine();
    }

    private static int lerInt(Scanner scanner, String prompt) {
        while (true) {
            try {
                if (prompt != null && !prompt.isEmpty()) {
                    System.out.print(prompt);
                }
                String line = scanner.nextLine();
                return Integer.parseInt(line);
            } catch (NumberFormatException e) {
                System.out.println("Entrada inválida. Por favor, digite um número.");
            }
        }
    }

    private static double lerDouble(Scanner scanner, String prompt) {
        while (true) {
            try {
                System.out.print(prompt);
                String line = scanner.nextLine().replace(',', '.');
                return Double.parseDouble(line);
            } catch (NumberFormatException e) {
                System.out.println("Entrada inválida. Por favor, digite um valor numérico.");
            }
        }
    }

    private static void pausar(Scanner scanner) {
        System.out.println("\n------------------------------------------");
        System.out.print("Pressione ENTER para voltar ao Menu Principal...");
        scanner.nextLine();
    }

    private static void cadastrarLoja(Scanner scanner) {
        System.out.println("\n--- CADASTRO DE NOVA LOJA/RESTAURANTE ---");
        String nomeRestaurante = lerString(scanner, "Nome do Restaurante: ");
        String cnpj = lerString(scanner, "CNPJ: ");
        String endereco = lerString(scanner, "Endereço da Loja: ");
        String horario = lerString(scanner, "Horário de Funcionamento (Ex: 09h-22h): ");
        String emailGerente = lerString(scanner, "Email do Gerente (para acesso): ");
        String senha = lerString(scanner, "Senha: ");

        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = "INSERT INTO lojas (nome_restaurante, cnpj, endereco, horario_funcionamento, email_gerente, senha) VALUES (?, ?, ?, ?, ?, ?)";
            PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            stmt.setString(1, nomeRestaurante);
            stmt.setString(2, cnpj);
            stmt.setString(3, endereco);
            stmt.setString(4, horario);
            stmt.setString(5, emailGerente);
            stmt.setString(6, senha);
            stmt.executeUpdate();

            ResultSet rs = stmt.getGeneratedKeys();
            if (rs.next()) {
                int idLoja = rs.getInt(1);
                System.out.println("\n[SUCESSO] Loja '" + nomeRestaurante + "' cadastrada com sucesso!");

                System.out.println("\n--- CADASTRO DO CARDÁPIO INICIAL ---");
                String adicionarMais = "s";

                while (adicionarMais.equalsIgnoreCase("s")) {
                    System.out.println("\nNovo Item:");
                    String nomeProduto = lerString(scanner, "Nome do Item: ");
                    double preco = lerDouble(scanner, "Preço (R$): ");
                    String descricao = lerString(scanner, "Descrição: ");

                    String sqlProduto = "INSERT INTO produtos (id_loja, nome, preco, descricao) VALUES (?, ?, ?, ?)";
                    PreparedStatement stmtProduto = conn.prepareStatement(sqlProduto);
                    stmtProduto.setInt(1, idLoja);
                    stmtProduto.setString(2, nomeProduto);
                    stmtProduto.setDouble(3, preco);
                    stmtProduto.setString(4, descricao);
                    stmtProduto.executeUpdate();

                    System.out.println("[ITEM ADICIONADO] " + nomeProduto);
                    adicionarMais = lerString(scanner, "Deseja adicionar outro item ao cardápio? (s/n): ");
                }
            }
        } catch (SQLException e) {
            System.err.println("Erro ao cadastrar loja: " + e.getMessage());
        }
    }

    private static void cadastrarEntregador(Scanner scanner) {
        System.out.println("\n--- CADASTRO DE NOVO ENTREGADOR ---");
        String nome = lerString(scanner, "Nome completo: ");
        String rg = lerString(scanner, "RG: ");
        String cpf = lerString(scanner, "CPF: ");
        String cnh = lerString(scanner, "CNH: ");
        String tipoVeiculo = lerString(scanner, "Tipo de Veículo (Ex: Moto, Carro): ");
        String placa = lerString(scanner, "Placa do Veículo: ");
        String email = lerString(scanner, "Email do Entregador: ");
        String senha = lerString(scanner, "Senha: ");

        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = "INSERT INTO entregadores (nome_completo, rg, cpf_documento, cnh, tipo_veiculo, placa, email, senha) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setString(1, nome);
            stmt.setString(2, rg);
            stmt.setString(3, cpf);
            stmt.setString(4, cnh);
            stmt.setString(5, tipoVeiculo);
            stmt.setString(6, placa);
            stmt.setString(7, email);
            stmt.setString(8, senha);
            stmt.executeUpdate();

            System.out.println("\n[SUCESSO] Entregador '" + nome + "' cadastrado com sucesso!");
        } catch (SQLException e) {
            System.err.println("Erro ao cadastrar entregador: " + e.getMessage());
        }
    }

    private static void cadastrarCliente(Scanner scanner) {
        System.out.println("\n--- CADASTRO DE NOVO CLIENTE ---");
        String nome = lerString(scanner, "Nome completo: ");
        String cpf = lerString(scanner, "CPF: ");
        String endereco = lerString(scanner, "Endereço: ");
        String telefone = lerString(scanner, "Telefone: ");
        String email = lerString(scanner, "Email: ");
        String senha = lerString(scanner, "Senha: ");

        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = "INSERT INTO clientes (nome_completo, cpf, endereco, telefone, email, senha) VALUES (?, ?, ?, ?, ?, ?)";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setString(1, nome);
            stmt.setString(2, cpf);
            stmt.setString(3, endereco);
            stmt.setString(4, telefone);
            stmt.setString(5, email);
            stmt.setString(6, senha);
            stmt.executeUpdate();

            System.out.println("\n[SUCESSO] Cliente " + nome + " cadastrado!");
        } catch (SQLException e) {
            System.err.println("Erro ao cadastrar cliente: " + e.getMessage());
        }
    }

    private static void cadastrarNovo(Scanner scanner) {
        System.out.println("\n--- 1. CADASTRAR NOVO USUÁRIO/ENTIDADE ---");
        System.out.println("Qual tipo de cadastro deseja realizar?");
        System.out.println("1. Cliente | 2. Loja/Restaurante | 3. Entregador");

        int tipoCadastro = lerInt(scanner, "Escolha o tipo de cadastro (1-3): ");

        switch (tipoCadastro) {
            case 1: cadastrarCliente(scanner); break;
            case 2: cadastrarLoja(scanner); break;
            case 3: cadastrarEntregador(scanner); break;
            default: System.out.println("Opção de tipo de cadastro inválida.");
        }
    }

    private static Cliente fazerLoginCliente(Scanner scanner) {
        System.out.println("\n--- SUB-OPÇÃO: LOGIN CLIENTE ---");
        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = "SELECT * FROM clientes WHERE ativo = TRUE";
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(sql);

            List<Cliente> clientes = new ArrayList<>();
            while (rs.next()) {
                clientes.add(new Cliente(
                        rs.getInt("id_cliente"),
                        rs.getString("nome_completo"),
                        rs.getString("cpf"),
                        rs.getString("endereco"),
                        rs.getString("telefone"),
                        rs.getString("email"),
                        rs.getString("senha")
                ));
            }

            if (clientes.isEmpty()) {
                System.out.println("Nenhum cliente cadastrado.");
                return null;
            }

            System.out.println("Clientes cadastrados:");
            for (int i = 0; i < clientes.size(); i++) {
                System.out.println((i + 1) + ". " + clientes.get(i).getNomeCompleto());
            }

            int escolha = lerInt(scanner, "Escolha o número do cliente para logar: ") - 1;
            if (escolha >= 0 && escolha < clientes.size()) {
                Cliente cliente = clientes.get(escolha);
                System.out.println("\n[SUCESSO] Cliente " + cliente.getNomeCompleto() + " logado!");
                return cliente;
            }
        } catch (SQLException e) {
            System.err.println("Erro ao fazer login: " + e.getMessage());
        }
        return null;
    }

    private static Loja fazerLoginLoja(Scanner scanner) {
        System.out.println("\n--- SUB-OPÇÃO: LOGIN LOJA ---");
        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = "SELECT * FROM lojas WHERE ativo = TRUE";
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(sql);

            List<Loja> lojas = new ArrayList<>();
            while (rs.next()) {
                lojas.add(new Loja(
                        rs.getInt("id_loja"),
                        rs.getString("nome_restaurante"),
                        rs.getString("cnpj"),
                        rs.getString("endereco"),
                        rs.getString("horario_funcionamento"),
                        rs.getString("email_gerente"),
                        rs.getString("senha")
                ));
            }

            if (lojas.isEmpty()) {
                System.out.println("Nenhuma loja cadastrada.");
                return null;
            }

            System.out.println("Lojas cadastradas:");
            for (int i = 0; i < lojas.size(); i++) {
                System.out.println((i + 1) + ". " + lojas.get(i).getNomeRestaurante());
            }

            int escolha = lerInt(scanner, "Escolha o número da loja para ativar: ") - 1;
            if (escolha >= 0 && escolha < lojas.size()) {
                Loja loja = lojas.get(escolha);
                System.out.println("\n[SUCESSO] Loja " + loja.getNomeRestaurante() + " ativada!");
                return loja;
            }
        } catch (SQLException e) {
            System.err.println("Erro ao fazer login: " + e.getMessage());
        }
        return null;
    }

    private static Entregador fazerLoginEntregador(Scanner scanner) {
        System.out.println("\n--- SUB-OPÇÃO: LOGIN ENTREGADOR ---");
        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = "SELECT * FROM entregadores WHERE ativo = TRUE";
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(sql);

            List<Entregador> entregadores = new ArrayList<>();
            while (rs.next()) {
                entregadores.add(new Entregador(
                        rs.getInt("id_entregador"),
                        rs.getString("nome_completo"),
                        rs.getString("rg"),
                        rs.getString("cpf_documento"),
                        rs.getString("cnh"),
                        rs.getString("tipo_veiculo"),
                        rs.getString("placa"),
                        rs.getString("email"),
                        rs.getString("senha"),
                        rs.getString("disponibilidade")
                ));
            }

            if (entregadores.isEmpty()) {
                System.out.println("Nenhum entregador cadastrado.");
                return null;
            }

            System.out.println("Entregadores cadastrados:");
            for (int i = 0; i < entregadores.size(); i++) {
                System.out.println((i + 1) + ". " + entregadores.get(i).getNomeCompleto());
            }

            int escolha = lerInt(scanner, "Escolha o número do entregador para ativar: ") - 1;
            if (escolha >= 0 && escolha < entregadores.size()) {
                Entregador entregador = entregadores.get(escolha);
                System.out.println("\n[SUCESSO] Entregador " + entregador.getNomeCompleto() + " ativado!");
                return entregador;
            }
        } catch (SQLException e) {
            System.err.println("Erro ao fazer login: " + e.getMessage());
        }
        return null;
    }

    private static void fazerLogin(Scanner scanner, Main instance) {
        instance.setClienteLogado(null);
        instance.setLojaAtiva(null);
        instance.setEntregadorAtivo(null);

        System.out.println("\n--- 2. LOGIN/ALTERNAR USUÁRIO ---");
        System.out.println("Qual tipo de usuário você é?");
        System.out.println("1. Cliente | 2. Loja/Restaurante | 3. Entregador");

        int tipoLogin = lerInt(scanner, "Escolha o tipo de usuário (1-3): ");

        switch (tipoLogin) {
            case 1: instance.setClienteLogado(fazerLoginCliente(scanner)); break;
            case 2: instance.setLojaAtiva(fazerLoginLoja(scanner)); break;
            case 3: instance.setEntregadorAtivo(fazerLoginEntregador(scanner)); break;
            default: System.out.println("Opção de tipo de usuário inválida.");
        }
    }

    private Cliente clienteLogado = null;
    private Loja lojaAtiva = null;
    private Entregador entregadorAtivo = null;

    public void setClienteLogado(Cliente c) { this.clienteLogado = c; }
    public void setLojaAtiva(Loja l) { this.lojaAtiva = l; }
    public void setEntregadorAtivo(Entregador e) { this.entregadorAtivo = e; }
    public Cliente getClienteLogado() { return clienteLogado; }
    public Loja getLojaAtiva() { return lojaAtiva; }
    public Entregador getEntregadorAtivo() { return entregadorAtivo; }

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        GerenciadorPedidos gerenciador = new GerenciadorPedidos();
        Main sistema = new Main();

        System.out.println("==========================================");
        System.out.println("    CONNECT DELIVERY - O SEU HUB DE ENTREGAS");
        System.out.println("==========================================");

        int opcao = -1;
        while (opcao != 0) {
            System.out.println("\n==========================================");
            System.out.println("               MENU PRINCIPAL");
            System.out.println("==========================================");
            System.out.println("Bem-vindo(a) ao Connect Delivery!");
            System.out.println("------------------------------------------");

            String usuarioAtivo = "Ninguém logado";
            if (sistema.getClienteLogado() != null) {
                usuarioAtivo = "Cliente Ativo: " + sistema.getClienteLogado().getNomeCompleto();
            } else if (sistema.getLojaAtiva() != null) {
                usuarioAtivo = "Loja Ativa: " + sistema.getLojaAtiva().getNomeRestaurante();
            } else if (sistema.getEntregadorAtivo() != null) {
                usuarioAtivo = "Entregador Ativo: " + sistema.getEntregadorAtivo().getNomeCompleto();
            }
            System.out.println(usuarioAtivo);
            System.out.println("------------------------------------------");

            System.out.println("Por favor, escolha uma opção:");
            System.out.println();
            System.out.println("1. Cadastrar Novo Usuário/Entidade");
            System.out.println("2. Fazer Login/Alternar Usuário");
            System.out.println("3. Realizar Novo Pedido (Cliente)");
            System.out.println("4. Acompanhar Pedidos (Loja Ativa)");
            System.out.println("5. Simular Entrega (Entregador Ativo)");
            System.out.println("6. Relatório de Entregues (Admin)");
            System.out.println("0. Sair");
            System.out.println("------------------------------------------");

            System.out.print("> ");
            opcao = lerInt(scanner, "");

            switch (opcao) {
                case 1:
                    cadastrarNovo(scanner);
                    break;
                case 2:
                    fazerLogin(scanner, sistema);
                    break;
                case 3:
                    if (sistema.getClienteLogado() != null) {
                        realizarPedido(scanner, gerenciador, sistema.getClienteLogado());
                    } else {
                        System.out.println("\nERRO: Faça login/cadastro (Opção 1 ou 2) como CLIENTE para pedir.");
                    }
                    break;
                case 4:
                    if (sistema.getLojaAtiva() != null) {
                        acompanharPedidos(scanner, gerenciador, sistema.getLojaAtiva());
                    } else {
                        System.out.println("\nERRO: Ative uma loja (Opção 2) para ver pedidos.");
                    }
                    break;
                case 5:
                    if (sistema.getEntregadorAtivo() != null) {
                        simularEntrega(scanner, gerenciador, sistema.getEntregadorAtivo());
                    } else {
                        System.out.println("\nERRO: Ative um entregador (Opção 2) para simular a entrega.");
                    }
                    break;
                case 6:
                    gerenciador.relatorioHistoricoEntregues();
                    break;
                case 0:
                    System.out.println("Obrigado por usar o Connect Delivery. Saindo...");
                    break;
                default:
                    System.out.println("Opção inválida. Tente novamente.");
            }

            if (opcao != 0) {
                pausar(scanner);
            }
        }
        scanner.close();
    }

    private static void realizarPedido(Scanner scanner, GerenciadorPedidos gerenciador, Cliente cliente) {
        System.out.println("\n--- 3. REALIZAR PEDIDO (Cliente: " + cliente.getNomeCompleto() + ") ---");

        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = "SELECT * FROM lojas WHERE ativo = TRUE";
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(sql);

            List<Loja> lojas = new ArrayList<>();
            while (rs.next()) {
                lojas.add(new Loja(
                        rs.getInt("id_loja"),
                        rs.getString("nome_restaurante"),
                        rs.getString("cnpj"),
                        rs.getString("endereco"),
                        rs.getString("horario_funcionamento"),
                        rs.getString("email_gerente"),
                        rs.getString("senha")
                ));
            }

            if (lojas.isEmpty()) {
                System.out.println("Não há lojas cadastradas.");
                return;
            }

            System.out.println("Restaurantes disponíveis:");
            for (int i = 0; i < lojas.size(); i++) {
                System.out.println((i + 1) + ". " + lojas.get(i).getNomeRestaurante());
            }

            int escolhaLoja = lerInt(scanner, "Escolha o número da Loja: ") - 1;
            if (escolhaLoja < 0 || escolhaLoja >= lojas.size()) {
                System.out.println("Escolha inválida.");
                return;
            }

            Loja lojaSelecionada = lojas.get(escolhaLoja);
            List<Produto> cardapio = lojaSelecionada.getCardapio();

            if (cardapio.isEmpty()) {
                System.out.println("Cardápio vazio. Não é possível fazer o pedido.");
                return;
            }

            System.out.println("\nCardápio da " + lojaSelecionada.getNomeRestaurante() + ":");
            for (int i = 0; i < cardapio.size(); i++) {
                System.out.println((i + 1) + ". " + cardapio.get(i).toString());
            }

            // Criar pedido temporário para adicionar itens
            String observacoes = lerString(scanner, "Observações do pedido (opcional): ");
            int idPedido = gerenciador.criarPedido(cliente, lojaSelecionada, observacoes);

            if (idPedido == -1) {
                System.out.println("Erro ao criar pedido.");
                return;
            }

            int escolhaItem = -1;
            while (escolhaItem != 0) {
                escolhaItem = lerInt(scanner, "Adicionar item (Digite o número, ou 0 para finalizar o pedido): ");

                if (escolhaItem > 0 && escolhaItem <= cardapio.size()) {
                    int quantidade = lerInt(scanner, "Quantidade: ");
                    if (quantidade > 0) {
                        Produto produto = cardapio.get(escolhaItem - 1);
                        gerenciador.adicionarItemPedido(idPedido, produto.getId(), quantidade);
                        System.out.println("Item adicionado: " + produto.getNome() + " x" + quantidade);

                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                } else if (escolhaItem != 0) {
                    System.out.println("Item inválido.");
                }
            }

            String confirma = lerString(scanner, "Confirma o pedido e pagamento online? (s/n): ");
            if (confirma.equalsIgnoreCase("s")) {
                System.out.println("[SUCESSO] Pedido #" + idPedido + " realizado! Loja será notificada.");
                lojaSelecionada.confirmarPedido(idPedido);
                lojaSelecionada.prepararParaColeta(idPedido);
            } else {
                // Cancelar pedido
                try (Connection connCancel = DatabaseConnection.getConnection()) {
                    String sqlCancel = "UPDATE pedidos SET status = 'CANCELADO' WHERE id_pedido = ?";
                    PreparedStatement stmtCancel = connCancel.prepareStatement(sqlCancel);
                    stmtCancel.setInt(1, idPedido);
                    stmtCancel.executeUpdate();
                    System.out.println("Pedido cancelado.");
                }
            }

        } catch (SQLException e) {
            System.err.println("Erro ao realizar pedido: " + e.getMessage());
        }
    }

    private static void acompanharPedidos(Scanner scanner, GerenciadorPedidos gerenciador, Loja lojaAtiva) {
        System.out.println("\n--- 4. ACOMPANHAR PEDIDOS (Loja: " + lojaAtiva.getNomeRestaurante() + ") ---");

        gerenciador.visualizarPedidosEmAndamentoParaLoja(lojaAtiva);

        String idInput = lerString(scanner, "Digite o ID do pedido para rastreamento (ou ENTER para voltar): ");

        if (!idInput.isEmpty()) {
            try {
                int idPedido = Integer.parseInt(idInput);
                Pedido pedido = gerenciador.getPedidoPorId(idPedido, lojaAtiva);

                if (pedido != null) {
                    System.out.println("\n--- RASTREAMENTO DO PEDIDO #" + idPedido + " ---");
                    System.out.println("Status: " + pedido.getStatus());
                    System.out.println("Situação de Prazo: " + gerenciador.verificarPrazo(pedido));
                    System.out.println("Valor Total: R$ " + String.format("%.2f", pedido.getValorTotal()));
                } else {
                    System.out.println("Pedido não encontrado ou não pertence a esta loja.");
                }
            } catch (NumberFormatException e) {
                System.out.println("ID inválido.");
            }
        }
    }

    private static void simularEntrega(Scanner scanner, GerenciadorPedidos gerenciador, Entregador entregadorAtivo) {
        System.out.println("\n--- 5. SIMULAR ENTREGA (Entregador: " + entregadorAtivo.getNomeCompleto() + ") ---");

        if (entregadorAtivo.getDisponibilidade() == DisponibilidadeEntregador.OFFLINE) {
            String mudarStatus = lerString(scanner, "Seu status está OFFLINE. Deseja ficar ONLINE? (s/n): ");
            if (mudarStatus.equalsIgnoreCase("s")) {
                entregadorAtivo.setDisponibilidade(DisponibilidadeEntregador.ONLINE);
            } else {
                System.out.println("Entrega cancelada. Entregador deve estar ONLINE.");
                return;
            }
        }

        // Listar pedidos prontos para coleta
        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = "SELECT p.*, l.nome_restaurante, c.nome_completo " +
                    "FROM pedidos p " +
                    "JOIN lojas l ON p.id_loja = l.id_loja " +
                    "JOIN clientes c ON p.id_cliente = c.id_cliente " +
                    "WHERE p.status = 'PRONTO_COLETA' " +
                    "ORDER BY p.hora_pedido";
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(sql);

            System.out.println("\n--- Pedidos Prontos para Coleta ---");
            boolean temPedidos = false;
            while (rs.next()) {
                temPedidos = true;
                System.out.printf("ID: %d | Loja: %s | Cliente: %s | Total: R$ %.2f\n",
                        rs.getInt("id_pedido"),
                        rs.getString("nome_restaurante"),
                        rs.getString("nome_completo"),
                        rs.getDouble("valor_total")
                );
            }

            if (!temPedidos) {
                System.out.println("Nenhum pedido pronto para coleta no momento.");
                return;
            }

        } catch (SQLException e) {
            System.err.println("Erro ao buscar pedidos: " + e.getMessage());
            return;
        }

        String idInput = lerString(scanner, "\nDigite o ID do pedido para ACEITAR a entrega: ");

        try {
            int idPedido = Integer.parseInt(idInput);

            // Aceitar entrega
            try (Connection conn = DatabaseConnection.getConnection()) {
                String sql = "UPDATE pedidos SET status = 'SAIU_ENTREGA', id_entregador = ? " +
                        "WHERE id_pedido = ? AND status = 'PRONTO_COLETA'";
                PreparedStatement stmt = conn.prepareStatement(sql);
                stmt.setInt(1, entregadorAtivo.getId());
                stmt.setInt(2, idPedido);
                int rows = stmt.executeUpdate();

                if (rows > 0) {
                    System.out.println("[Entregador] Pedido #" + idPedido + " coletado. Status atualizado para 'Saiu para Entrega'.");

                    System.out.println("\n[Sistema] Entregador chegou ao destino.");
                    String codigo = lerString(scanner, "Digite o CÓDIGO DE CONFIRMAÇÃO (use '1234' para sucesso): ");

                    gerenciador.finalizarEntrega(idPedido, codigo);
                } else {
                    System.out.println("Pedido inválido ou ainda não está PRONTO PARA COLETA.");
                }
            }

        } catch (NumberFormatException e) {
            System.out.println("ID inválido.");
        } catch (SQLException e) {
            System.err.println("Erro ao aceitar entrega: " + e.getMessage());
        }
    }
}