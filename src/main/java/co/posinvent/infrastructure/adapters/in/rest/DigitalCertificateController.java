package co.posinvent.infrastructure.adapters.in.rest;

import co.posinvent.application.dto.DigitalCertificateResponse;
import co.posinvent.application.usecase.ManageDigitalCertificateUseCase;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/certificates")
@PreAuthorize("hasRole('ADMIN')")
public class DigitalCertificateController {

    private final ManageDigitalCertificateUseCase useCase;

    public DigitalCertificateController(ManageDigitalCertificateUseCase useCase) {
        this.useCase = useCase;
    }

    @GetMapping
    public List<DigitalCertificateResponse> listAll() {
        return useCase.listAll();
    }

    @GetMapping("/{id}")
    public DigitalCertificateResponse getById(@PathVariable UUID id) {
        return useCase.getById(id);
    }

    @PostMapping
    public DigitalCertificateResponse upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "password", required = false) String password,
            @RequestParam(value = "name", required = false) String name) throws IOException {
        var fileName = name != null ? name : file.getOriginalFilename();
        return useCase.upload(file.getBytes(), password, fileName);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable UUID id) {
        useCase.delete(id);
    }
}
