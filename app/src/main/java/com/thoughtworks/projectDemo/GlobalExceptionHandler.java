package com.thoughtworks.projectDemo;

import com.thoughtworks.projectDemo.model.ProblemModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(value =Exception.class)
    public ResponseEntity<ProblemModel> exceptionHandler(Exception e){
        return new ResponseEntity<>(new ProblemModel().code(500).msg(e.getMessage()),
                HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
