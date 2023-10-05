package com.uco.yourplus.serviceyourplus.usecase.reciever.producto.implementation;

import com.uco.yourplus.crosscuttingyourplus.exceptions.service.ServiceCustomException;
import com.uco.yourplus.serviceyourplus.domain.ProductoDomain;
import com.uco.yourplus.serviceyourplus.domain.ResponseDomain;
import com.uco.yourplus.serviceyourplus.domain.enumeration.StateResponse;
import com.uco.yourplus.serviceyourplus.usecase.producto.ActualizarProducto;
import com.uco.yourplus.serviceyourplus.usecase.reciever.producto.RabbitMQUpdateReceiverProducto;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

@Service
public class RabbitMQUpdateReceiverProductoImpl implements RabbitMQUpdateReceiverProducto {

    private final ActualizarProducto useCase;

    public RabbitMQUpdateReceiverProductoImpl(ActualizarProducto useCase) {
        this.useCase = useCase;
    }

    @RabbitListener(queues = "${yourplus.management.producto.queue.update}")
    @Override
    public void execute(ProductoDomain domain) {
        StateResponse stateResponse = StateResponse.SUCCESS;
        ResponseDomain responseDomain = new ResponseDomain();
        try {
            useCase.execute(domain);
            responseDomain.setStateResponse(stateResponse);
            responseDomain.setMessage("Producto actualizado con éxito");
        }catch (ServiceCustomException exception){
            stateResponse = StateResponse.ERROR;
            responseDomain.setStateResponse(stateResponse);
            if (exception.isTechnicalException()){
                responseDomain.setMessage("Algo salio mal actualizando el producto, intenta nuevamente");
            }else {
                responseDomain.setMessage(exception.getMessage());
            }
        }catch (Exception exception){
            stateResponse = StateResponse.ERROR;
            responseDomain.setStateResponse(stateResponse);
            responseDomain.setMessage("Ocurrió un error fatal, intentalo en unos minutos");
        }finally {
            //TODO: Agregar el sender de rabbit
        }
    }
}
