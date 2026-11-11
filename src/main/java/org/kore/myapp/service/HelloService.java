package org.kore.myapp.service;

import lombok.RequiredArgsConstructor;
import org.kore.myapp.core.entity.User;
import org.kore.myapp.core.exception.UserNotFoundException;
import org.kore.myapp.core.port.HelloUseCase;
import org.kore.myapp.core.port.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class HelloService implements HelloUseCase {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public String sayHello(String username) {
        User user = userRepository.findByUsername(username).orElseThrow(() -> new UserNotFoundException(username));
        return "Hello " + user.getName();
    }
}
