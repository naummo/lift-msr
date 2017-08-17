CREATE TABLE IF NOT EXISTS lift_results_cnn(
  id int NOT NULL AUTO_INCREMENT,
  device_name varchar(1000),
  n_batches int,
  n_inputs int,
  n_conv_layers int,
  n_fc_layers int,
  image_size int,

  n_kernels_l0 int,
  kernel_size_l0 int,
  kernel_stride_l0 int,
  input_tile_size_l0 int,
  input_tile_stride_l0 int,
  els_per_thread_l0 int,
  kernels_per_group_l0 int,

  n_kernels_l1 int,
  kernel_size_l1 int,
  kernel_stride_l1 int,
  input_tile_size_l1 int,
  input_tile_stride_l1 int,
  els_per_thread_l1 int,
  kernels_per_group_l1 int,
  
  pool_size int,
  l1_out_len_original int,
  l1_out_len_new int,

  input_len_l2_nonpadded int,
  input_len_l2_padded int,
  n_neurons_l2_nonpadded int,
  n_neurons_l2_padded int,
  mults_per_thread_l2 int,
  neurons_per_wrg_l2 int,

  input_len_l3_nonpadded int,
  input_len_l3_padded int,
  n_neurons_l3_nonpadded int,
  n_neurons_l3_padded int,
  mults_per_thread_l3 int,
  neurons_per_wrg_l3 int,

  runtime_l0 float,
  runtime_l1 float,
  runtime_l2 float,
  runtime_l3 float,

  ran boolean NOT NULL,
  abort_reason varchar(1000),
  success boolean,
  verified boolean,
  experiment_id int,
  datetime DATETIME,
  PRIMARY KEY (id)
);