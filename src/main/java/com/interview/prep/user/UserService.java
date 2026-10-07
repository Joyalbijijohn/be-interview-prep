package com.interview.prep.user;

import com.interview.prep.common.NotFoundException;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository repository;

    public UserService(UserRepository repository) {
        this.repository = repository;
    }

    public UserResponse get(Long id) {
        return repository.findById(id).map(UserResponse::from)
                .orElseThrow(() -> new NotFoundException("User " + id + " not found"));
    }

    public List<UserResponse> list() {
        return repository.findAll(Sort.by("id")).stream().map(UserResponse::from).toList();
    }
}
