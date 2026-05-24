# Command: /test-api
## Mô tả: Tạo Unit Test cho API

Tạo unit test và integration test:

## Controller Test

```java
@WebMvcTest({Entity}Controller.class)
class {Entity}ControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @MockBean
    private {Entity}Service {entity}Service;
    
    @Test
    void shouldGetAll{Entity}s() throws Exception {
        when({entity}Service.findAll()).thenReturn(List.of());
        
        mockMvc.perform(get("/api/v1/{entities}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }
    
    @Test
    void shouldGet{Entity}ById() throws Exception {
        when({entity}Service.findById(1L)).thenReturn(dto);
        
        mockMvc.perform(get("/api/v1/{entities}/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(1));
    }
    
    @Test
    void shouldCreate{Entity}() throws Exception {
        when({entity}Service.create(any())).thenReturn(dto);
        
        mockMvc.perform(post("/api/v1/{entities}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andExpect(status().isCreated());
    }
    
    @Test
    void shouldReturn404WhenNotFound() throws Exception {
        when({entity}Service.findById(999L))
            .thenThrow(new ResourceNotFoundException("Not found"));
        
        mockMvc.perform(get("/api/v1/{entities}/999"))
            .andExpect(status().isNotFound());
    }
}
```

## Service Test

```java
@ExtendWith(MockitoExtension.class)
class {Entity}ServiceTest {
    
    @Mock
    private {Entity}Repository {entity}Repository;
    
    @InjectMocks
    private {Entity}Service {entity}Service;
    
    @Test
    void shouldFindAll{Entity}s() {
        when({entity}Repository.findAll()).thenReturn(List.of());
        
        var result = {entity}Service.findAll();
        
        assertNotNull(result);
        verify({entity}Repository).findAll();
    }
}
```

## Ví dụ:
- "/test-api Staff" → tạo StaffControllerTest, StaffServiceTest
- "/test-api Schedule" → tạo ScheduleControllerTest
