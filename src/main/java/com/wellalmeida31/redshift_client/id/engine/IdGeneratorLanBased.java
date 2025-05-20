package com.wellalmeida31.redshift_client.id.engine;

import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.id.IdentifierGenerator;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Objects;

import static java.lang.System.currentTimeMillis;


/**
 * Gerador de identificadores únicos distribuídos inspirado na estratégia de Snowflake.
 *
 * <p>Essa classe implementa a interface {@link IdentifierGenerator} do Hibernate para fornecer
 * identificadores únicos baseados em timestamps, garantindo unicidade em sistemas distribuídos.</p>
 *
 * <h2>Estrutura do Identificador Gerado:</h2>
 * <ul>
 *     <li><b>Bits de Timestamp:</b> Representa o número de milissegundos desde uma época definida (epoch).</li>
 *     <li><b>Bits de Node ID:</b> Identifica o nó da aplicação, calculado com base no endereço IP do host.</li>
 *     <li><b>Bits de Sequência:</b> Garantem a unicidade de IDs gerados no mesmo milissegundo.</li>
 * </ul>
 *
 * <h2>Detalhes Técnicos:</h2>
 * <ul>
 *     <li><b>Época:</b> 1º de janeiro de 2023, 00:00:00 UTC.</li>
 *     <li><b>Node ID:</b> Determinado a partir do hash do endereço IP local, limitado a 10 bits.</li>
 *     <li><b>Field:</b> Pode ser usado como long ou String</li>
 *     <li><b>Sequência:</b> Incrementada para cada ID gerado no mesmo milissegundo, limitada a 12 bits.</li>
 *     <li><b>Colisões</b> Apresenta 0% de taxa de colisão para programação imperativa, podendo apresentar
 *     colisão em ambientes multi thread com alta taxa de geração de id por segundo</li>
 *     <li><b>Capacidade:</b> Pode gerar 160 quatrilhões de IDs</li>
 * </ul>
 *
 * <h2>Comportamento:</h2>
 * <ul>
 *     <li>Se múltiplos IDs forem gerados no mesmo milissegundo, a sequência será incrementada.</li>
 *     <li>Se a sequência atingir seu limite, o gerador esperará até o próximo milissegundo para continuar.</li>
 * </ul>
 *
 * <h2>Exemplo de Uso:</h2>
 * <pre>{@code
 * IdGenerator generator = new IdGenerator();
 * long id = (long) generator.generate(null, null);
 * System.out.println("Generated ID: " + id);
 * }</pre>
 *
 *  <pre>{@code
 *  @Id
 *  @GeneratedValue(generator = "id-generator")
 *  @GenericGenerator(name = "id-generator", type = IdGenerator.class)
 *  private Long id;
 *  }</pre>
 *
 *  <pre>{@code
 *  @Id
 *  @GeneratedValue(generator = "id-generator")
 *  @GenericGenerator(name = "id-generator", type = IdGenerator.class)
 *  @JsonSerialize(using = ToStringSerializer.class)
 *  private Long id;
 *  }</pre>
 *
 * @author Wellington Almeida
 * @version 1.1
 * @since 2025-05-19
 * @see IdentifierGenerator
 */
public class IdGeneratorLanBased implements IdentifierGenerator {
    /**
     * Época base utilizada para calcular os timestamps.
     * Representa o timestamp de 1º de janeiro de 2023, 00:00:00 UTC.
     */
    private static final long EPOCH = 1672531200000L;
    private static final int NODE_BITS = 10;
    private static final int SEQUENCE_BITS = 12;
    private static final long MAX_NODE_ID = (1L << NODE_BITS) - 1;
    private static final long MAX_SEQUENCE = (1L << SEQUENCE_BITS) - 1;

    private final long nodeId;
    private long lastTimestamp = -1L;
    private long sequence = 0L;

    /**
     * Construtor padrão.
     *
     * <p>Calcula o Node ID com base no endereço IP do host atual.</p>
     */
    public IdGeneratorLanBased() {
        this.nodeId = createNodeId();
    }

    /**
     * Gera um identificador único com base no timestamp, Node ID e sequência.
     *
     * @param sharedSessionContractImplementor contexto da sessão compartilhada do Hibernate.
     * @param o entidade associada ao identificador sendo gerado.
     * @return Object - um identificador único.
     */
    @Override
    public Object generate(SharedSessionContractImplementor sharedSessionContractImplementor, Object o) {
        long currentTimestamp = currentTimeMillis();

        if (currentTimestamp == lastTimestamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0) currentTimestamp = waitNextMillis(lastTimestamp);
        } else sequence = 0;

        lastTimestamp = currentTimestamp;

        return ((currentTimestamp - EPOCH) << (NODE_BITS + SEQUENCE_BITS)) | (nodeId << SEQUENCE_BITS) | sequence;
    }

    /**
     * Aguarda o próximo milissegundo para evitar colisões quando a sequência atinge o limite.
     *
     * @param lastTimestamp último timestamp utilizado.
     * @return long - o próximo timestamp em milissegundos.
     */
    private long waitNextMillis(long lastTimestamp) {
        long currentTimestamp = currentTimeMillis();
        while (currentTimestamp <= lastTimestamp) {
            currentTimestamp = currentTimeMillis();
        }
        return currentTimestamp;
    }

    /**
     * Gera o Node ID baseado no hash do endereço IP local ou configurado (pod / fargate / etc...) .
     *
     * @return long - o Node ID limitado a {@link #MAX_NODE_ID}.
     * @throws RuntimeException se não for possível obter o endereço IP do host.
     */
    private long createNodeId() {
        try {
            String hostAddress = Objects.requireNonNullElse(InetAddress.getLocalHost().getHostAddress(), "127.0.0.1");
            return Math.abs(hostAddress.hashCode()) & MAX_NODE_ID;
        } catch (UnknownHostException e) {
            throw new RuntimeException("Não foi possível gerar o NodeId", e);
        }
    }
}
