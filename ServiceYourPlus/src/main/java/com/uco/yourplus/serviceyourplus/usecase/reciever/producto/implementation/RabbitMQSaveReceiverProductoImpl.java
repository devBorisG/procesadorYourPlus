package com.uco.yourplus.serviceyourplus.usecase.reciever.producto.implementation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uco.yourplus.crosscuttingyourplus.exceptions.service.ServiceCustomException;
import com.uco.yourplus.crosscuttingyourplus.helper.json.MapperJsonObject;
import com.uco.yourplus.crosscuttingyourplus.properties.ProductoPropertiesCatalogProducer;
import com.uco.yourplus.serviceyourplus.domain.ProductoDomain;
import com.uco.yourplus.serviceyourplus.domain.ResponseDomain;
import com.uco.yourplus.serviceyourplus.domain.enumeration.StateResponse;
import com.uco.yourplus.serviceyourplus.usecase.producer.response.ConfigRabbitContentResponse;
import com.uco.yourplus.serviceyourplus.usecase.producto.RegistrarProducto;
import com.uco.yourplus.serviceyourplus.usecase.reciever.producto.RabbitMQSaveReceiverProducto;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
@EnableConfigurationProperties(ProductoPropertiesCatalogProducer.class)
public class RabbitMQSaveReceiverProductoImpl implements RabbitMQSaveReceiverProducto {

    private final RegistrarProducto useCase;

    private final MapperJsonObject mapperJsonObject;

    private final RabbitTemplate rabbitTemplate;

    private final ConfigRabbitContentResponse configRabbitContentResponse;

    private final ProductoPropertiesCatalogProducer producer;

    public RabbitMQSaveReceiverProductoImpl(RegistrarProducto useCase, MapperJsonObject mapperJsonObject,
                                            RabbitTemplate rabbitTemplate, ConfigRabbitContentResponse configRabbitContentResponse,
                                            @Qualifier("productoPropertiesCatalogProducer") ProductoPropertiesCatalogProducer producer) {
        this.useCase = useCase;
        this.mapperJsonObject = mapperJsonObject;
        this.rabbitTemplate = rabbitTemplate;
        this.configRabbitContentResponse = configRabbitContentResponse;
        this.producer = producer;
    }

    @RabbitListener(queues = "${yourplus.management.producto.queue.save}")
    @Override
    public void execute(String message) {
        try{
            ResponseDomain<ProductoDomain> responseDomain = setIdForMessage(message);
            MessageProperties messageProperties = configRabbitContentResponse.generateMessageProperties(responseDomain.getId());
            sentResponse(responseDomain, message, messageProperties);
        }catch (JsonProcessingException exception){
            throw ServiceCustomException.createTechnicalException(exception, "No se pudo agregar el id del mensaje");
        }catch (Exception exception){
            throw ServiceCustomException.createTechnicalException(exception, "Ocurrio un error inesperado");
        }
    }

    private ResponseDomain<ProductoDomain> setIdForMessage(String message) throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode jsonNode = objectMapper.readTree(message);
        ResponseDomain<ProductoDomain> responseDomain = new ResponseDomain<>();
        responseDomain.setId(UUID.fromString(jsonNode.get("id").asText()));
        return responseDomain;
    }

    private void sentResponse(ResponseDomain<ProductoDomain> responseDomain, String message, MessageProperties messageProperties){
        StateResponse stateResponse = StateResponse.SUCCESS;
        Optional<Message> bodyMessage = Optional.empty();
        try {
            ProductoDomain domain = mapperJsonObject.execute(message, ProductoDomain.class).get();
            useCase.execute(domain);
            responseDomain.setStateResponse(stateResponse);
            responseDomain.setMessage("Producto registrado con éxito");
            bodyMessage = configRabbitContentResponse.getBodyMessage(responseDomain, messageProperties);
        } catch (ServiceCustomException exception) {
            stateResponse = StateResponse.ERROR;
            responseDomain.setStateResponse(stateResponse);
            if (exception.isTechnicalException()) {
                responseDomain.setMessage("Algo salio mal registrando el producto, intenta nuevamente");
            } else {
                responseDomain.setMessage(exception.getMessage());
            }
            bodyMessage = configRabbitContentResponse.getBodyMessage(responseDomain, messageProperties);
        } catch (Exception exception) {
            stateResponse = StateResponse.ERROR;
            responseDomain.setStateResponse(stateResponse);
            responseDomain.setMessage("Ocurrió un error fatal, intentalo en unos minutos");
            bodyMessage = configRabbitContentResponse.getBodyMessage(responseDomain, messageProperties);
        } finally {
            rabbitTemplate.convertAndSend(producer.getExchange(), producer.getRoutingkey().getSave(), bodyMessage.get());
        }
    }
}
